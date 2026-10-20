# 覆盖率门禁：读取 JaCoCo 聚合报告，行覆盖低于阈值即失败。
# 用法（在 vane-server 下）：powershell -ExecutionPolicy Bypass -File .\scripts\verify-coverage.ps1
param(
    [double]$Threshold = 70.0
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$csv = Join-Path $root 'vane-bootstrap\target\site\jacoco-aggregate\jacoco.csv'

if (-not (Test-Path $csv)) {
    Write-Error "未找到聚合报告：$csv`n请先执行：.\mvnw.cmd -B test"
    exit 2
}

$rows = Import-Csv $csv
$covered = ($rows | Measure-Object -Property LINE_COVERED -Sum).Sum
$missed = ($rows | Measure-Object -Property LINE_MISSED -Sum).Sum
$total = $covered + $missed
$percent = if ($total -gt 0) { [math]::Round(100 * $covered / $total, 1) } else { 0 }

$branchCovered = ($rows | Measure-Object -Property BRANCH_COVERED -Sum).Sum
$branchMissed = ($rows | Measure-Object -Property BRANCH_MISSED -Sum).Sum
$branchTotal = $branchCovered + $branchMissed
$branchPercent = if ($branchTotal -gt 0) { [math]::Round(100 * $branchCovered / $branchTotal, 1) } else { 0 }

Write-Host ("行覆盖  : {0}% ({1}/{2})" -f $percent, $covered, $total)
Write-Host ("分支覆盖: {0}% ({1}/{2})" -f $branchPercent, $branchCovered, $branchTotal)

if ($percent -lt $Threshold) {
    Write-Error "行覆盖率 $percent% 低于门禁 $Threshold%（docs/09 §4）"
    exit 1
}

Write-Host "覆盖率门禁通过（阈值 $Threshold%）"
exit 0
