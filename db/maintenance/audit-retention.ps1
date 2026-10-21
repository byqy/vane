<#
    审计日志保留期清理（ADR-023 §8）。用维护账号执行；应用账号无 DELETE 权限。

    用法：
      powershell -ExecutionPolicy Bypass -File .\db\maintenance\audit-retention.ps1
      $env:AUDIT_RETENTION_DAYS = '730'
      $env:DB_URL = 'jdbc:postgresql://localhost:5432/vane'  # 或由 psql 客户端解析
#>
param(
    [string]$RetentionDays = $(if ($env:AUDIT_RETENTION_DAYS) { $env:AUDIT_RETENTION_DAYS } else { '730' }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'vane' }),
    [string]$Host_ = $(if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5432 }),
    [string]$User = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'postgres' }),
    [string]$Container = $(if ($env:DB_CONTAINER) { $env:DB_CONTAINER } else { 'vane-pg' })
)

$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'audit-retention.sql'

$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($psql) {
    $env:PGPASSWORD = $env:DB_PASSWORD
    & psql -h $Host_ -p $Port -U $User -d $Database -v retention_days=$RetentionDays -f $script
    if ($LASTEXITCODE -ne 0) { throw "psql 清理失败，退出码 $LASTEXITCODE" }
    Write-Host "审计日志清理完成：保留 $RetentionDays 天"
    exit 0
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) { throw '未找到 psql 或 docker，请安装其一后再执行' }

$sql = Get-Content $script -Raw
$sql = $sql -replace ':retention_days', $RetentionDays
$sql | & docker exec -i $Container psql -U $User -d $Database
if ($LASTEXITCODE -ne 0) { throw "docker exec psql 清理失败，退出码 $LASTEXITCODE" }
Write-Host "审计日志清理完成：保留 $RetentionDays 天"
