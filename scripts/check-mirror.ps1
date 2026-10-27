<#
.SYNOPSIS
  检查 vane 主仓（GitHub）与镜像仓（Gitee）的一致性。

.DESCRIPTION
  对比三处状态：
    1. 本地工作区：分支、HEAD、是否有未提交改动；
    2. 主仓与镜像的默认分支指向；
    3. 两边的标签集合与标签指向的提交。

  默认会先 fetch 两个远程（需要网络）。Gitee 镜像为只读仓，
  fetch 只读取、不会写入，因此可以安全使用。

.PARAMETER Primary
  主仓远程名，默认 origin（GitHub）。

.PARAMETER Mirror
  镜像仓远程名，默认 gitee。

.PARAMETER Branch
  要对比的分支，默认 main。

.PARAMETER NoFetch
  跳过 fetch，仅比较已有的远程跟踪引用（离线可用；标签对比会跳过）。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\check-mirror.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\check-mirror.ps1 -NoFetch

.NOTES
  退出码：0 = 一致；1 = 不一致；2 = 远程未配置或无法读取。

  脚本以 UTF-8 BOM 保存，确保 Windows PowerShell 5.1 正确读取中文。
#>
[CmdletBinding()]
param(
    [string]$Primary = 'origin',
    [string]$Mirror = 'gitee',
    [string]$Branch = 'main',
    [switch]$NoFetch
)

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    # git 的 stderr（进度、警告）不应被 PowerShell 当成终止性错误；
    # 统一通过 Invoke-Git 收集输出与退出码。
    function Invoke-Git {
        param(
            [Parameter(Mandatory)][string[]]$Arguments,
            [switch]$AllowFailure
        )
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $output = & git @Arguments 2>&1 | Out-String
        } finally {
            $ErrorActionPreference = $previous
        }
        $code = $LASTEXITCODE
        if ($code -ne 0 -and -not $AllowFailure) {
            throw "git $($Arguments -join ' ') 失败（退出码 $code）：$($output.Trim())"
        }
        return [pscustomobject]@{ Output = $output.Trim(); Code = $code }
    }

    function Write-Ok([string]$message) { Write-Host "√ $message" -ForegroundColor Green }
    function Write-Bad([string]$message) { Write-Host "× $message" -ForegroundColor Red }
    function Write-Warn([string]$message) { Write-Host "! $message" -ForegroundColor Yellow }

    function Test-Remote([string]$name) {
        $result = Invoke-Git -Arguments @('remote') -AllowFailure
        return @($result.Output -split "`r?`n") -contains $name
    }

    function Get-RemoteUrl([string]$name) {
        return (Invoke-Git -Arguments @('remote', 'get-url', $name) -AllowFailure).Output
    }

    function Get-RemoteTags([string]$remote) {
        $result = Invoke-Git -Arguments @('ls-remote', '--tags', $remote) -AllowFailure
        if ($result.Code -ne 0) {
            return [pscustomobject]@{ Ok = $false; Map = @{}; Error = $result.Output }
        }
        $map = @{}
        foreach ($line in ($result.Output -split "`r?`n")) {
            if (-not $line) { continue }
            $parts = $line -split "`t"
            if ($parts.Count -ne 2) { continue }
            $sha = $parts[0]
            $ref = $parts[1]
            if ($ref -like '*^{}') {
                # annotated tag 解引用后的提交
                $name = ($ref -replace '^refs/tags/', '') -replace '\^\{\}$', ''
                $map[$name] = $sha
            } else {
                $name = $ref -replace '^refs/tags/', ''
                if (-not $map.ContainsKey($name)) { $map[$name] = $sha }
            }
        }
        return [pscustomobject]@{ Ok = $true; Map = $map; Error = '' }
    }

    # ---------- 0. 远程存在性 ----------
    foreach ($pair in @(@('主仓', $Primary), @('镜像', $Mirror))) {
        if (-not (Test-Remote $pair[1])) {
            Write-Bad "未配置$($pair[0])远程 '$($pair[1])'"
            Write-Host "  git remote add $($pair[1]) <仓库地址>"
            exit 2
        }
    }

    Write-Host "主仓：$Primary  $(Get-RemoteUrl $Primary)" -ForegroundColor Cyan
    Write-Host "镜像：$Mirror  $(Get-RemoteUrl $Mirror)" -ForegroundColor Cyan

    # ---------- 1. 本地状态 ----------
    $branch = (Invoke-Git -Arguments @('rev-parse', '--abbrev-ref', 'HEAD')).Output
    $head = (Invoke-Git -Arguments @('rev-parse', 'HEAD')).Output
    $dirty = (Invoke-Git -Arguments @('status', '--porcelain')).Output
    $problems = 0

    Write-Host ''
    Write-Host "本地：分支 $branch，HEAD $($head.Substring(0, 7))"
    if ($dirty) {
        $count = @($dirty -split "`r?`n" | Where-Object { $_ }).Count
        Write-Warn "工作区有未提交改动（$count 个文件），对比结果可能不代表已发布内容"
    } else {
        Write-Ok '工作区干净'
    }
    if ($branch -ne $Branch) {
        Write-Warn "当前分支是 $branch，与对比分支 $Branch 不同"
    }

    # ---------- 2. fetch ----------
    if (-not $NoFetch) {
        Write-Host ''
        Write-Host '正在读取两个远程…'
        foreach ($remote in @($Primary, $Mirror)) {
            $fetch = Invoke-Git -Arguments @('fetch', $remote, '--prune', '--tags', '--quiet') -AllowFailure
            if ($fetch.Code -ne 0) {
                Write-Bad "fetch $remote 失败：$($fetch.Output)"
                exit 2
            }
        }
    }

    # ---------- 3. 分支一致性 ----------
    $primarySha = (Invoke-Git -Arguments @('rev-parse', '--verify', '--quiet', "refs/remotes/$Primary/$Branch") -AllowFailure).Output
    $mirrorSha = (Invoke-Git -Arguments @('rev-parse', '--verify', '--quiet', "refs/remotes/$Mirror/$Branch") -AllowFailure).Output

    Write-Host ''
    if (-not $primarySha) {
        Write-Bad "主仓缺少分支 $Branch（是否已 push？）"
        exit 2
    }
    Write-Host "主仓 $Branch：$($primarySha.Substring(0, 7))"

    if (-not $mirrorSha) {
        Write-Bad "镜像仓缺少分支 $Branch（Gitee 镜像可能尚未完成首次同步）"
        $problems++
    } else {
        Write-Host "镜像 $Branch：$($mirrorSha.Substring(0, 7))"
        if ($primarySha -eq $mirrorSha) {
            Write-Ok '分支内容一致'
        } else {
            Write-Bad "分支不一致：主仓 $($primarySha.Substring(0, 7)) vs 镜像 $($mirrorSha.Substring(0, 7))"
            $ahead = (Invoke-Git -Arguments @('rev-list', '--count', "$mirrorSha..$primarySha")).Output
            $behind = (Invoke-Git -Arguments @('rev-list', '--count', "$primarySha..$mirrorSha")).Output
            Write-Host "  镜像落后主仓 $ahead 个提交，领先 $behind 个提交"
            $problems++
        }
    }

    if ($branch -eq $Branch -and $primarySha -ne $head) {
        Write-Warn '本地 HEAD 与主仓 main 不同（本地可能未推送或未拉取）'
    }

    # ---------- 4. 标签一致性 ----------
    Write-Host ''
    if ($NoFetch) {
        Write-Warn '已跳过标签对比（-NoFetch 不读取远程标签）'
    } else {
        $primaryTags = Get-RemoteTags $Primary
        $mirrorTags = Get-RemoteTags $Mirror

        if (-not $primaryTags.Ok) {
            Write-Bad "无法读取主仓标签：$($primaryTags.Error)"
            $problems++
        } elseif (-not $mirrorTags.Ok) {
            Write-Bad "无法读取镜像标签：$($mirrorTags.Error)"
            $problems++
        } else {
            $missing = @($primaryTags.Map.Keys | Where-Object { -not $mirrorTags.Map.ContainsKey($_) })
            $extra = @($mirrorTags.Map.Keys | Where-Object { -not $primaryTags.Map.ContainsKey($_) })
            $differing = @($primaryTags.Map.Keys | Where-Object {
                    $mirrorTags.Map.ContainsKey($_) -and $mirrorTags.Map[$_] -ne $primaryTags.Map[$_]
                })

            Write-Host "主仓标签 $(@($primaryTags.Map.Keys).Count) 个，镜像标签 $(@($mirrorTags.Map.Keys).Count) 个"

            if (@($primaryTags.Map.Keys).Count -eq 0) {
                Write-Warn '主仓没有标签（可执行：git push origin --tags）'
            }
            if ($missing.Count -gt 0) {
                Write-Bad "镜像缺少标签：$($missing -join ', ')"
                $problems++
            }
            if ($extra.Count -gt 0) {
                Write-Warn "镜像存在主仓没有的标签（镜像只读，理论上不应出现）：$($extra -join ', ')"
                $problems++
            }
            if ($differing.Count -gt 0) {
                Write-Bad "标签指向不一致：$($differing -join ', ')"
                $problems++
            }
            if (@($primaryTags.Map.Keys).Count -gt 0 -and $missing.Count -eq 0 -and $differing.Count -eq 0) {
                Write-Ok '标签一致'
            }
        }
    }

    # ---------- 5. 结论 ----------
    Write-Host ''
    if ($problems -eq 0) {
        Write-Ok '两个平台一致'
        exit 0
    }
    Write-Bad "发现 $problems 处差异（Gitee 镜像为定时同步，可稍后重试或手动触发一次同步）"
    exit 1
} finally {
    Pop-Location
}
