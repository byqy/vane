# 数据库迁移验证脚本
# 用法：在普通 PowerShell（非受限环境）中执行
#   powershell -ExecutionPolicy Bypass -File .\db\verify.ps1
# 作用：启动临时 PostgreSQL 18 容器，依次执行迁移脚本，校验结果，默认结束后删除容器。

param(
    [string]$ContainerName = 'vane-pg',
    [int]$Port = 55432,
    [string]$DbName = 'vane',
    [switch]$KeepContainer
)

$ErrorActionPreference = 'Stop'
$migrationDir = Join-Path $PSScriptRoot 'migration'
$migrations = @(
    'V1__extensions_and_common.sql',
    'V2__iam_schema.sql',
    'V3__sys_schema.sql',
    'V4__cms_schema.sql',
    'V5__seed_reference.sql'
)

function Write-Step($text) { Write-Host "`n== $text ==" -ForegroundColor Cyan }

Write-Step 'Docker 连接检查'
$server = (docker version --format '{{.Server.Version}}' 2>&1)
if ($LASTEXITCODE -ne 0) {
    Write-Host ('无法连接 Docker：' + $server) -ForegroundColor Red
    Write-Host '请确认 Docker Desktop 已启动（任务栏鲸鱼图标静止）。'
    exit 1
}
Write-Host ('Docker Server: ' + $server)

Write-Step '清理同名容器'
docker rm -f $ContainerName 2>$null | Out-Null

Write-Step ('启动 PostgreSQL 18：' + $ContainerName)
docker run -d --name $ContainerName `
    -e POSTGRES_PASSWORD=postgres `
    -e POSTGRES_DB=$DbName `
    -p "${Port}:5432" `
    postgres:18 | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host '容器启动失败' -ForegroundColor Red; exit 1 }

Write-Step '等待数据库就绪'
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    docker exec $ContainerName pg_isready -U postgres -d $DbName *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    docker logs $ContainerName --tail 30
    Write-Host '数据库未就绪' -ForegroundColor Red
    exit 1
}
Write-Host '数据库已就绪'

Write-Step '执行迁移'
$failed = @()
foreach ($m in $migrations) {
    $path = Join-Path $migrationDir $m
    if (-not (Test-Path $path)) { Write-Host ('缺少文件：' + $m) -ForegroundColor Red; $failed += $m; continue }
    docker cp $path ("${ContainerName}:/tmp/" + $m) | Out-Null
    $out = docker exec $ContainerName psql -U postgres -d $DbName -v ON_ERROR_STOP=1 -f ("/tmp/" + $m) 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host ('FAIL ' + $m) -ForegroundColor Red
        $out | Select-Object -Last 15
        $failed += $m
        break
    }
    Write-Host ('OK   ' + $m) -ForegroundColor Green
}

if ($failed.Count -gt 0) {
    Write-Host "`n迁移失败，容器保留用于排查：" -ForegroundColor Red
    Write-Host ("  docker exec -it " + $ContainerName + " psql -U postgres -d " + $DbName)
    exit 1
}

Write-Step '校验结果'
$queries = [ordered]@{
    '表数量'      = "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE';"
    '索引数量'    = "select count(*) from pg_indexes where schemaname='public';"
    '角色'        = "select count(*) from iam_role;"
    '权限码（授权表去重）' = "select count(distinct permission_code) from iam_role_permission;"
    '角色授权'    = "select count(*) from iam_role_permission;"
    '系统参数'    = "select count(*) from sys_config;"
    '字典项'      = "select count(*) from sys_dict_item;"
    '模板译文'    = "select count(*) from sys_message_template_i18n;"
}
foreach ($k in $queries.Keys) {
    $v = (docker exec $ContainerName psql -U postgres -d $DbName -t -c $queries[$k] 2>&1).Trim()
    Write-Host ("  {0}：{1}" -f $k, $v)
}

Write-Step '表清单'
docker exec $ContainerName psql -U postgres -d $DbName -t -c "select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by table_name;" 2>&1 |
    Where-Object { $_.Trim() -ne '' } | ForEach-Object { Write-Host ('  ' + $_.Trim()) }

Write-Step '外键与部分唯一索引抽查'
docker exec $ContainerName psql -U postgres -d $DbName -t -c "select count(*) from pg_constraint where contype='f';" 2>&1 |
    ForEach-Object { Write-Host ('  外键数量：' + $_.Trim()) }
docker exec $ContainerName psql -U postgres -d $DbName -t -c "select indexname from pg_indexes where schemaname='public' and indexdef like '%WHERE%' order by indexname;" 2>&1 |
    Where-Object { $_.Trim() -ne '' } | ForEach-Object { Write-Host ('  部分索引：' + $_.Trim()) }

if (-not $KeepContainer) {
    Write-Step '清理容器'
    docker rm -f $ContainerName | Out-Null
    Write-Host '已完成'
} else {
    Write-Host ("`n容器保留：" + $ContainerName + "（端口 " + $Port + "）")
}
