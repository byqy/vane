<#
.SYNOPSIS
    重新生成 OpenAPI 契约快照 openapi/vane.json（ADR-030 契约层）。

.DESCRIPTION
    从运行实例的 /v3/api-docs 下载契约并写入仓库快照。契约变更后必须同时更新
    openapi/vane.json 与 docs/03，否则 CI 的契约比对会失败。

    用法：
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

$url = "$($BaseUrl.TrimEnd('/'))/v3/api-docs"
$headers = @{ Authorization = "Bearer $Token"; Accept = 'application/json' }
$response = Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -TimeoutSec 60
$bytes = $response.RawContentStream.ToArray()
$target = [System.IO.Path]::GetFullPath($SnapshotPath)
[System.IO.File]::WriteAllBytes($target, $bytes)

Write-Host "已更新契约快照：$target（$($bytes.Length) 字节）"
Write-Host "请同步更新 docs/03 中受影响的接口说明。" -ForegroundColor Yellow