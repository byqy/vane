<#
.SYNOPSIS
    生成 docs/catalog.json：文档索引的机器可读投影（ADR-030 P1）。

.DESCRIPTION
    条目字段固定六项（ADR-030 评审决议 6）：
      id         文档 id
      authority  权威主题
      tasks      适用任务
      version    版本（从文档头部读取，保证与正文同步）
      status     状态（从文档头部读取）
      dependsOn  依赖的其他分册
      anchor     对应文件路径（仓库相对）

    用法：
      powershell -ExecutionPolicy Bypass -File .\scripts\gen-doc-catalog.ps1          # 写入
      powershell -ExecutionPolicy Bypass -File .\scripts\gen-doc-catalog.ps1 -Check   # 仅校验，不一致退出码 1
#>
[CmdletBinding()]
param(
    [switch]$Check,
    [string]$DocsDir,
    [string]$OutputFile
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($DocsDir)) {
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $DocsDir = Join-Path $scriptDir '..\docs'
}
if ([string]::IsNullOrWhiteSpace($OutputFile)) {
    $OutputFile = Join-Path $DocsDir 'catalog.json'
}

# 编辑性元数据：权威主题、适用任务与依赖关系（版本/状态从文档头部自动读取）
$catalog = @(
    @{ id = '总纲'; file = '需求设计总纲.md'; authority = '范围、文档体系、决策与验收';
       tasks = @('了解项目定位', '查阅决策点', '核对里程碑'); dependsOn = @() },
    @{ id = '01'; file = '01-技术栈与规范基线.md'; authority = '版本与规范的唯一权威';
       tasks = @('选择依赖版本', '确认工程结构', '查编码规范', '查质量门禁'); dependsOn = @('总纲') },
    @{ id = '02'; file = '02-需求规格说明.md'; authority = '功能需求与权限模型';
       tasks = @('查功能需求', '查角色与权限', '确认优先级'); dependsOn = @('总纲', '01') },
    @{ id = '03'; file = '03-HTTP语义与错误模型规范.md'; authority = '接口契约的唯一权威';
       tasks = @('查状态码契约', '查错误码与恢复指令', '查分页与幂等', '查认证令牌'); dependsOn = @('01', '总纲') },
    @{ id = '04'; file = '04-国际化规范.md'; authority = '文案与内容的多语言闭环';
       tasks = @('新增文案', '处理语言回退', '查支持语言列表'); dependsOn = @('01', '03') },
    @{ id = '05'; file = '05-数据模型与数据字典.md'; authority = '表结构的唯一权威';
       tasks = @('查表结构', '新增迁移脚本', '查枚举与约束'); dependsOn = @('01', '02') },
    @{ id = '06'; file = '06-安全与合规设计.md'; authority = '安全与合规基线';
       tasks = @('查权限校验', '查数据范围', '查敏感数据处理'); dependsOn = @('01', '03', '05') },
    @{ id = '07'; file = '07-可观测性与运维设计.md'; authority = '日志、指标与运维约定';
       tasks = @('查日志规范', '查健康检查', '查运维脚本'); dependsOn = @('01', '06') },
    @{ id = '08'; file = '08-前端架构与设计系统.md'; authority = '前端架构与交互约定';
       tasks = @('查目录结构', '查组件与路由约定', '查主题令牌'); dependsOn = @('01', '03') },
    @{ id = '09'; file = '09-测试与质量门禁.md'; authority = '测试分层与质量门禁的唯一权威';
       tasks = @('查测试要求', '跑质量门禁', '查覆盖率阈值'); dependsOn = @('01', '03', '05', '06') },
    @{ id = '10'; file = '10-ADR'; authority = '关键决策的背景、选项与影响';
       tasks = @('查决策依据', '新增 ADR', '核对决策边界'); dependsOn = @('总纲') },
    @{ id = '11'; file = '11-术语表.md'; authority = '统一用语';
       tasks = @('核对术语', '新增术语'); dependsOn = @('总纲') }
)

function Get-DocMeta {
    param([string]$Path)
    $version = $null
    $status = $null
    # 文档为 UTF-8 无 BOM；Windows PowerShell 5.1 默认按 ANSI 读取会乱码，必须显式指定编码
    $header = [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8) | Select-Object -First 12
    foreach ($line in $header) {
        if (-not $version -and $line -match '^\|\s*(?:文档)?版本\s*\|\s*([^|]+?)\s*\|') { $version = $matches[1] }
        if (-not $status -and $line -match '^\|\s*(?:文档)?状态\s*\|\s*([^|]+?)\s*\|') { $status = $matches[1] }
    }
    return [pscustomobject]@{ Version = $version; Status = $status }
}

$entries = New-Object System.Collections.Generic.List[object]
foreach ($item in $catalog) {
    $full = Join-Path $DocsDir $item.file
    if (-not (Test-Path $full)) { continue }
    $anchor = 'docs/' + $item.file
    $version = $null
    $status = $null
    if (Test-Path $full -PathType Leaf) {
        $meta = Get-DocMeta -Path $full
        $version = $meta.Version -replace '\*', ''
        $status = $meta.Status -replace '\*', ''
        if ($version) { $version = $version.Trim() }
        if ($status) { $status = $status.Trim() }
    } elseif (Test-Path $full -PathType Container) {
        # 目录型条目（ADR）：篇数即版本规模，状态取接受与否的汇总
        $count = @(Get-ChildItem $full -Filter 'ADR-*.md').Count
        $version = "$count 篇"
        $status = '已接受'
    }
    $entries.Add([ordered]@{
        id        = $item.id
        authority = $item.authority
        tasks     = @($item.tasks)
        version   = $version
        status    = $status
        dependsOn = @($item.dependsOn)
        anchor    = $anchor
    })
}

$document = [ordered]@{
    generatedFrom = 'docs/*.md 头部元数据 + 编辑性映射'
    documents     = $entries
}

$json = $document | ConvertTo-Json -Depth 8
if ($Check) {
    if (-not (Test-Path $OutputFile)) {
        Write-Host "catalog.json 不存在：$OutputFile" -ForegroundColor Red
        exit 1
    }
    $existing = [System.IO.File]::ReadAllText((Resolve-Path $OutputFile), [System.Text.Encoding]::UTF8)
    if (($existing -replace "`r`n", "`n").Trim() -ne ($json -replace "`r`n", "`n").Trim()) {
        Write-Host "catalog.json 与文档头部不一致，请运行 scripts/gen-doc-catalog.ps1 重新生成" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "catalog.json 与文档头部一致"
    exit 0
}

[System.IO.File]::WriteAllText($OutputFile, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "已生成：$OutputFile（$($entries.Count) 份文档）"
