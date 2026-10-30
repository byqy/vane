<#
.SYNOPSIS
    校验运行时 OpenAPI 契约与仓库快照 openapi/vane.json 一致（ADR-030 契约层）。

.DESCRIPTION
    下载运行实例的 /v3/api-docs，与 openapi/vane.json 做规范化比对。
    不一致时退出码 1，并提示如何重新生成快照。

    用法：
      powershell -ExecutionPolicy Bypass -File .\scripts\verify-openapi.ps1 -BaseUrl http://127.0.0.1:8080 -Token <access-token>

    重新生成快照（契约变更后必须同时更新 docs/03 与快照）：
      powershell -ExecutionPolicy Bypass -File .\scripts\update-openapi.ps1 -BaseUrl http://127.0.0.1:8080 -Token <access-token>
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$SnapshotPath
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SnapshotPath)) {
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $SnapshotPath = Join-Path $scriptDir '..\openapi\vane.json'
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

function Get-OpenApiText {
    param([string]$Url, [hashtable]$Headers)
    $response = Invoke-WebRequest -Uri $Url -Headers $Headers -UseBasicParsing -TimeoutSec 60
    # 自行按 UTF-8 解码：Windows PowerShell 5.1 对无 charset 的 JSON 会按 ISO-8859-1 处理，中文描述会乱码
    return [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
}

$url = "$($BaseUrl.TrimEnd('/'))/v3/api-docs"
$headers = @{ Authorization = "Bearer $Token"; Accept = 'application/json' }

try {
    $text = Get-OpenApiText -Url $url -Headers $headers
} catch {
    Write-Error "无法获取 OpenAPI 契约：$url —— $($_.Exception.Message)"
    exit 2
}

$actual = Get-CanonicalJson -Text $text
if (-not (Test-Path $SnapshotPath)) {
    Write-Error "快照不存在：$SnapshotPath（先执行 scripts/update-openapi.ps1 生成）"
    exit 2
}
$expected = Get-CanonicalJson -Text ([System.IO.File]::ReadAllText((Resolve-Path $SnapshotPath), [System.Text.Encoding]::UTF8))

if ($actual -ceq $expected) {
    Write-Host "OpenAPI 契约与快照一致（$SnapshotPath）"
    exit 0
}

Write-Host "OpenAPI 契约与快照不一致：$SnapshotPath" -ForegroundColor Yellow
Write-Host "契约变更需同时更新：openapi/vane.json、docs/03；重新生成命令：" -ForegroundColor Yellow
Write-Host "  powershell -ExecutionPolicy Bypass -File .\scripts\update-openapi.ps1 -BaseUrl $BaseUrl -Token <token>"
exit 1