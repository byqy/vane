<#
.SYNOPSIS
    Agent 可操作性自检（ADR-030 P0-5）：一条命令确认系统可被代理操作。

.DESCRIPTION
    只做只读检查，不产生任何业务数据：
      1. health    —— 服务健康
      2. login     —— 服务账号可登录并拿到令牌
      3. manifest  —— 能力清单可读，操作与错误码条目齐全
      4. contract  —— OpenAPI 契约与 openapi/vane.json 一致
      5. manifest-contract —— 能力清单与 openapi/capabilities.json 一致
      6. semantics —— 错误响应带恢复指令 action 与 retryable
      7. permission—— 服务账号的权限边界符合预期（可读不可写越权项）
      8. idempotency —— 幂等键协议生效（非法键被拒且无副作用）

    输出 JSON 到标准输出；全部通过退出码 0，否则 1。

    用法：
      powershell -ExecutionPolicy Bypass -File .\scripts\agent-check.ps1 `
          -BaseUrl http://127.0.0.1:8080 `
          -CredentialsFile C:\Users\byqy\.dsh\vane-agent-credentials.json
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$CredentialsFile = "$env:USERPROFILE\.dsh\vane-agent-credentials.json",
    [string]$SnapshotDir
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SnapshotDir)) {
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $SnapshotDir = Join-Path $scriptDir '..\openapi'
}

$results = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail)
    $script:results.Add([pscustomobject]@{ name = $Name; ok = $Ok; detail = $Detail })
}

function Invoke-Json {
    param([string]$Method, [string]$Uri, [hashtable]$Headers, [object]$Body)
    $params = @{
        Uri             = $Uri
        Method          = $Method
        Headers         = $Headers
        UseBasicParsing = $true
        TimeoutSec      = 30
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }
    $status = 0
    $text = ''
    try {
        $response = Invoke-WebRequest @params
        $status = $response.StatusCode
        $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    } catch {
        # Windows PowerShell 5.1 没有 -SkipHttpErrorCheck：错误响应要从异常里取
        $status = 0
        $text = [string]$_.ErrorDetails.Message
        $errorResponse = $_.Exception.Response
        if ($null -ne $errorResponse) {
            $status = [int]$errorResponse.StatusCode
            $typeName = $errorResponse.GetType().FullName
            if ([string]::IsNullOrWhiteSpace($text)) {
                if ($typeName -eq 'System.Net.Http.HttpResponseMessage') {
                    $text = $errorResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                } else {
                    $reader = New-Object System.IO.StreamReader($errorResponse.GetResponseStream())
                    $text = $reader.ReadToEnd()
                    $reader.Close()
                }
            }
        }
        if ($status -eq 0) {
            throw
        }
    }
    $parsed = $null
    if (-not [string]::IsNullOrWhiteSpace($text)) {
        try { $parsed = $text | ConvertFrom-Json } catch { $parsed = $null }
    }
    return [pscustomobject]@{ Status = $status; Text = $text; Json = $parsed }
}

function Get-CanonicalJson {
    param([string]$Text)
    $parsed = $Text | ConvertFrom-Json
    if ($parsed.PSObject.Properties.Name -contains 'servers') {
        $parsed.PSObject.Properties.Remove('servers')
    }
    return ($parsed | ConvertTo-Json -Depth 100 -Compress)
}

# 1. 健康
try {
    $health = Invoke-Json -Method 'GET' -Uri "$($BaseUrl.TrimEnd('/'))/actuator/health" -Headers @{}
    Add-Check -Name 'health' -Ok ($health.Status -eq 200 -and $health.Json.status -eq 'UP') `
        -Detail "HTTP $($health.Status) $($health.Json.status)"
} catch {
    Add-Check -Name 'health' -Ok $false -Detail $_.Exception.Message
}

# 2. 登录
$token = $null
$permissions = @()
try {
    if (-not (Test-Path $CredentialsFile)) {
        throw "凭据文件不存在：$CredentialsFile"
    }
    $cred = [System.IO.File]::ReadAllText($CredentialsFile, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    $login = Invoke-Json -Method 'POST' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/login" -Headers @{} `
        -Body @{ username = $cred.username; password = $cred.password }
    if ($login.Status -ne 200 -or -not $login.Json.accessToken) {
        throw "登录失败：HTTP $($login.Status)"
    }
    $token = $login.Json.accessToken
    $me = Invoke-Json -Method 'GET' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/me" -Headers @{ Authorization = "Bearer $token" }
    $permissions = @($me.Json.permissions)
    Add-Check -Name 'login' -Ok $true -Detail "用户 $($me.Json.username)，权限码 $($permissions.Count) 个"
} catch {
    Add-Check -Name 'login' -Ok $false -Detail $_.Exception.Message
}

$auth = if ($token) { @{ Authorization = "Bearer $token" } } else { @{} }

# 3. 能力清单结构
try {
    $cap = Invoke-Json -Method 'GET' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/agent/capabilities" -Headers $auth
    $operations = @($cap.Json.operations)
    $errors = @($cap.Json.errorCatalog)
    $missingAction = @($errors | Where-Object { -not $_.action })
    $ok = $cap.Status -eq 200 -and $operations.Count -gt 0 -and $errors.Count -gt 0 -and $missingAction.Count -eq 0
    Add-Check -Name 'manifest' -Ok $ok -Detail "$($operations.Count) 个操作 / $($errors.Count) 个错误码 / 缺恢复指令 $($missingAction.Count) 个"
} catch {
    Add-Check -Name 'manifest' -Ok $false -Detail $_.Exception.Message
}

# 4 & 5. 契约快照
$contractTargets = @(
    @{ Name = 'contract'; Url = "$($BaseUrl.TrimEnd('/'))/v3/api-docs"; File = 'vane.json' },
    @{ Name = 'manifest-contract'; Url = "$($BaseUrl.TrimEnd('/'))/api/v1/agent/capabilities"; File = 'capabilities.json' }
)
foreach ($target in $contractTargets) {
    try {
        $snapshot = Join-Path $SnapshotDir $target.File
        if (-not (Test-Path $snapshot)) { throw "快照不存在：$snapshot" }
        $actual = Invoke-Json -Method 'GET' -Uri $target.Url -Headers $auth
        $expected = Get-CanonicalJson -Text ([System.IO.File]::ReadAllText((Resolve-Path $snapshot), [System.Text.Encoding]::UTF8))
        $same = (Get-CanonicalJson -Text $actual.Text) -ceq $expected
        Add-Check -Name $target.Name -Ok $same -Detail $(if ($same) { "与 $($target.File) 一致" } else { "与 $($target.File) 不一致" })
    } catch {
        Add-Check -Name $target.Name -Ok $false -Detail $_.Exception.Message
    }
}

# 6. 错误语义
try {
    $unauthorized = Invoke-Json -Method 'GET' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/admin/users" -Headers @{}
    $code = $unauthorized.Json.code
    $action = $unauthorized.Json.action
    $retryable = $unauthorized.Json.retryable
    $ok = $unauthorized.Status -eq 401 -and $code -and $action -and ($null -ne $retryable)
    Add-Check -Name 'semantics' -Ok $ok -Detail "HTTP $($unauthorized.Status) code=$code action=$action retryable=$retryable"
} catch {
    Add-Check -Name 'semantics' -Ok $false -Detail $_.Exception.Message
}

# 7. 权限边界：可读业务数据、不可创建用户
try {
    $read = Invoke-Json -Method 'GET' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/admin/notices?page%5Bnumber%5D=0&page%5Bsize%5D=1" -Headers $auth
    # 服务账号没有删除权限：对不存在的公告做删除，应先被权限拦下（无副作用）
    $forbidden = Invoke-Json -Method 'DELETE' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/admin/notices/00000000-0000-0000-0000-000000000000" -Headers $auth
    $ok = $read.Status -eq 200 -and $forbidden.Status -eq 403 -and $forbidden.Json.code -eq 'AUTH.FORBIDDEN'
    Add-Check -Name 'permission' -Ok $ok -Detail "读 HTTP $($read.Status)；越权创建 HTTP $($forbidden.Status) $($forbidden.Json.code)"
} catch {
    Add-Check -Name 'permission' -Ok $false -Detail $_.Exception.Message
}

# 8. 审计事件投影
try {
    $events = Invoke-Json -Method 'GET' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/agent/events?limit=1" -Headers $auth
    $items = @($events.Json.items)
    $ok = $events.Status -eq 200 -and $null -ne $events.Json.items
    Add-Check -Name 'events' -Ok $ok -Detail "HTTP $($events.Status) 事件 $($items.Count) 条"
} catch {
    Add-Check -Name 'events' -Ok $false -Detail $_.Exception.Message
}

# 9. 幂等协议：非法键被拒且不产生副作用
try {
    $rejected = Invoke-Json -Method 'POST' -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/admin/notices" -Headers ($auth + @{ 'Idempotency-Key' = 'x' }) `
        -Body @{ level = 'INFO'; pinned = $false; translations = @() }
    $ok = $rejected.Status -eq 400 -and $rejected.Json.code -eq 'REQUEST.MALFORMED'
    Add-Check -Name 'idempotency' -Ok $ok -Detail "非法键 HTTP $($rejected.Status) $($rejected.Json.code)"
} catch {
    Add-Check -Name 'idempotency' -Ok $false -Detail $_.Exception.Message
}

$ok = -not ($results | Where-Object { -not $_.ok })
$report = [pscustomobject]@{
    base       = $BaseUrl
    checkedAt  = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
    actor      = if ($token) { 'service-account' } else { 'anonymous' }
    permissions = $permissions.Count
    ok         = $ok
    checks     = $results
}

$report | ConvertTo-Json -Depth 6
if (-not $ok) { exit 1 }
exit 0
