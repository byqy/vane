<#
.SYNOPSIS
    校验运行时契约与仓库快照一致（ADR-030 契约层 + 能力层）。

.DESCRIPTION
    比对两项产物：
      1. /v3/api-docs            ↔ openapi/vane.json
      2. /api/v1/agent/capabilities ↔ openapi/capabilities.json
    任一不一致即退出码 1，并提示如何重新生成。

    用法：
      powershell -ExecutionPolicy Bypass -File .\scripts\verify-contracts.ps1 -BaseUrl http://127.0.0.1:8080 -Token <access-token>
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$SnapshotDir
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SnapshotDir)) {
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $SnapshotDir = Join-Path $scriptDir '..\openapi'
}

function Get-CanonicalJson {
    param([string]$Text)
    $parsed = $Text | ConvertFrom-Json
    # servers 节点由运行时端口生成，随环境变化，不属于契约内容
    if ($parsed.PSObject.Properties.Name -contains 'servers') {
        $parsed.PSObject.Properties.Remove('servers')
    }
    return ($parsed | ConvertTo-Json -Depth 100 -Compress)
}

function Get-EndpointText {
    param([string]$Url, [hashtable]$Headers)
    $response = Invoke-WebRequest -Uri $Url -Headers $Headers -UseBasicParsing -TimeoutSec 60
    # 自行按 UTF-8 解码：Windows PowerShell 5.1 对无 charset 的 JSON 会按 ISO-8859-1 处理，中文描述会乱码
    return [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
}

$headers = @{ Authorization = "Bearer $Token"; Accept = 'application/json' }
$targets = @(
    @{ Name = 'OpenAPI 契约'; Url = "$($BaseUrl.TrimEnd('/'))/v3/api-docs"; File = 'vane.json' },
    @{ Name = '能力清单'; Url = "$($BaseUrl.TrimEnd('/'))/api/v1/agent/capabilities"; File = 'capabilities.json' }
)

$failed = $false
foreach ($target in $targets) {
    $snapshot = Join-Path $SnapshotDir $target.File
    try {
        $actual = Get-CanonicalJson -Text (Get-EndpointText -Url $target.Url -Headers $headers)
    } catch {
        Write-Host "$($target.Name)：无法获取 $($target.Url) —— $($_.Exception.Message)" -ForegroundColor Red
        $failed = $true
        continue
    }
    if (-not (Test-Path $snapshot)) {
        Write-Host "$($target.Name)：快照不存在 $snapshot" -ForegroundColor Yellow
        $failed = $true
        continue
    }
    $expected = Get-CanonicalJson -Text ([System.IO.File]::ReadAllText((Resolve-Path $snapshot), [System.Text.Encoding]::UTF8))
    if ($actual -ceq $expected) {
        Write-Host "$($target.Name)：与快照一致（$snapshot）"
    } else {
        Write-Host "$($target.Name)：与快照不一致（$snapshot）" -ForegroundColor Yellow
        $failed = $true
    }
}

if ($failed) {
    Write-Host "契约变更需同时更新 openapi/ 下的快照与 docs/03；重新生成命令：" -ForegroundColor Yellow
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\scripts\update-contracts.ps1 -BaseUrl $BaseUrl -Token <token>"
    exit 1
}
exit 0