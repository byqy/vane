<#
.SYNOPSIS
    重新生成契约快照：openapi/vane.json 与 openapi/capabilities.json（ADR-030）。

.DESCRIPTION
    从运行实例下载 /v3/api-docs 与 /api/v1/agent/capabilities 并写入仓库快照。
    契约变更后必须同时更新快照与 docs/03，否则 CI 的契约比对会失败。

    用法：
      powershell -ExecutionPolicy Bypass -File .\scripts\update-contracts.ps1 -BaseUrl http://127.0.0.1:8080 -Token <access-token>
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
if (-not (Test-Path $SnapshotDir)) {
    New-Item -ItemType Directory -Path $SnapshotDir -Force | Out-Null
}

$headers = @{ Authorization = "Bearer $Token"; Accept = 'application/json' }
$targets = @(
    @{ Url = "$($BaseUrl.TrimEnd('/'))/v3/api-docs"; File = 'vane.json' },
    @{ Url = "$($BaseUrl.TrimEnd('/'))/api/v1/agent/capabilities"; File = 'capabilities.json' }
)

foreach ($target in $targets) {
    $response = Invoke-WebRequest -Uri $target.Url -Headers $headers -UseBasicParsing -TimeoutSec 60
    $bytes = $response.RawContentStream.ToArray()
    $path = Join-Path $SnapshotDir $target.File
    [System.IO.File]::WriteAllBytes($path, $bytes)
    Write-Host "已更新：$path（$($bytes.Length) 字节）"
}
Write-Host "请同步更新 docs/03 中受影响的接口说明。" -ForegroundColor Yellow