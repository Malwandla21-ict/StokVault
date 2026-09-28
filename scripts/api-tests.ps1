<#
.SYNOPSIS
    End-to-end checks of the deployed StokVault API: security, per-group roles, tenant isolation,
    validation, business rules, the audit chain, exports and the batch job.

.DESCRIPTION
    Needs the demo data (scripts\seed-demo-data.ps1). Almost every check is a request the API must
    refuse, so the demo data is left as it was. The exceptions: the lockout check uses its own test
    account, and one eligibility job run is started.

        powershell -ExecutionPolicy Bypass -File scripts\api-tests.ps1

    Exit code 0 = everything passed.
#>
param(
    [string]$BaseUrl = "http://localhost:8081/stokvault/api",
    [string]$ServerLog = "$env:USERPROFILE\OneDrive\Documents\payara7\glassfish\domains\domain1\logs\server.log",
    [string]$AdminPhone = "0600000000",
    [string]$AdminPassword
)

$script:passed = 0
$script:failed = 0
$DemoPassword = "Demo-2026"

# A valid South African ID number: YYMMDD + 4-digit sequence + 0 + 8 + Luhn check digit
function New-SaId([string]$yymmdd, [string]$sequence) {
    $first12 = $yymmdd + $sequence + "08"
    $sum = 0
    for ($i = 0; $i -lt 12; $i++) {
        $d = [int]::Parse($first12[$i])
        if ($i % 2 -eq 1) { $d *= 2; if ($d -gt 9) { $d -= 9 } }
        $sum += $d
    }
    return $first12 + ((10 - $sum % 10) % 10)
}

function Read-LogFrom([long]$offset) {
    $fs = [IO.File]::Open($ServerLog, 'Open', 'Read', 'ReadWrite')
    try { $fs.Seek($offset, 'Begin') | Out-Null; return (New-Object IO.StreamReader($fs)).ReadToEnd() } finally { $fs.Close() }
}

# Returns @{ Status; Body (parsed JSON or $null); Raw; Bytes } and never throws on 4xx/5xx
function Send([string]$Method, [string]$Path, $Body = $null, [string]$User = $null, [string]$Secret = $DemoPassword,
              [string]$Scheme = "Basic", [string]$RawBody = $null) {
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; ContentType = "application/json"; UseBasicParsing = $true; Headers = @{} }
    if ($User) { $params.Headers.Authorization = "$Scheme " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${User}:${Secret}")) }
    if ($null -ne $Body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6)) }
    if ($RawBody) { $params.Body = $RawBody }
    $bytes = $null
    try {
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode
        $content = $r.Content
        if ($content -is [byte[]]) { $bytes = $content; $content = [Text.Encoding]::UTF8.GetString($content) }
    } catch [System.Net.WebException] {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        $content = $_.ErrorDetails.Message
    }
    $parsed = $null
    # (PowerShell's -and/-or have equal precedence, so the brackets matter)
    if ($content -and ($content.TrimStart().StartsWith("{") -or $content.TrimStart().StartsWith("["))) {
        try { $parsed = $content | ConvertFrom-Json } catch { }
    }
    return @{ Status = $status; Body = $parsed; Raw = $content; Bytes = $bytes }
}

function Check([string]$Name, [int]$Expected, $Response, [scriptblock]$Extra = $null) {
    $ok = $Response.Status -eq $Expected
    if ($ok -and $Extra) { $ok = [bool](& $Extra $Response.Body $Response) }
    if ($ok) {
        $script:passed++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:failed++
        $raw = if ($Response.Raw) { $Response.Raw.Substring(0, [Math]::Min(300, $Response.Raw.Length)) } else { "" }
        Write-Host "  FAIL  $Name (expected $Expected, got $($Response.Status)) $raw" -ForegroundColor Red
    }
}

# ---- demo data ------------------------------------------------------------------------------
if (-not $AdminPassword) {
    # Payara rotates server.log, so search the older log files too
    $allLogs = (Get-ChildItem (Split-Path $ServerLog) -Filter "server.log*" | Sort-Object LastWriteTime | ForEach-Object {
        $fs = [IO.File]::Open($_.FullName, 'Open', 'Read', 'ReadWrite'); try { (New-Object IO.StreamReader($fs)).ReadToEnd() } finally { $fs.Close() } }) -join "`n"
    $m = [regex]::Matches($allLogs, "one-time generated password: (\S+)\.")
    if ($m.Count -gt 0) { $AdminPassword = $m[$m.Count - 1].Groups[1].Value }
}
$thandi = "0821234567"; $sipho = "0832345678"; $lerato = "0713456789"; $bongani = "0724567890"; $naledi = "0845678901"; $zanele = "0766789012"

$groups = (Send GET "/groups" $null $AdminPhone $AdminPassword).Body
$ubuntu = $groups | Where-Object name -eq "Ubuntu Savings Club"
$burial = $groups | Where-Object name -eq "Siyakhana Burial Society"
if (-not $ubuntu) { Write-Host "Demo data not found: run scripts\seed-demo-data.ps1 first." -ForegroundColor Yellow; exit 1 }
$u = $ubuntu.id
$ubuntuMembers = (Send GET "/groups/$u/members" $null $thandi).Body
$bonganiId = ($ubuntuMembers | Where-Object memberName -eq "Bongani Khumalo").memberId
$openCycle = @((Send GET "/groups/$u/cycles" $null $thandi).Body | Where-Object status -eq "OPEN")[0]
$failedPayout = @((Send GET "/groups/$u/payouts?status=SCHEDULED" $null $thandi).Body)[0]

Write-Host "Authentication (Jakarta Security)"
Check "health check is public" 200 (Send GET "/health") { param($b) $b.database -eq "up" }
Check "API without credentials is 401 JSON" 401 (Send GET "/me") { param($b) $b.status -eq 401 }
Check "wrong password is 401" 401 (Send GET "/me" $null $thandi "not-the-password")
Check "correct password works; roles are returned" 200 (Send GET "/me" $null $thandi) {
    param($b) ($b.roles -contains "TREASURER") -and ($b.roles -contains "MEMBER") -and -not ($b.roles -contains "ADMIN") }
Check "national ID is never returned in full" 200 (Send GET "/me" $null $thandi) { param($b) $b.member.nationalIdMasked -match '^\*{9}\d{4}$' }
Check "login code request doesn't reveal unknown numbers" 202 (Send POST "/auth/login-code" @{ phoneNumber = "0799999999" })

# Lockout (SDD 4.6) on a dedicated test account
$testPhone = "0799990001"
$existing = @((Send GET "/members?q=$testPhone" $null $AdminPhone $AdminPassword).Body)
if ($existing.Count -eq 0) {
    Send POST "/members" @{ fullName = "Lockout Test"; nationalId = (New-SaId "900101" "5000"); phoneNumber = $testPhone; popiaConsent = $true } $AdminPhone $AdminPassword | Out-Null
    $offset = (Get-Item $ServerLog).Length
    Send POST "/auth/login-code" @{ phoneNumber = $testPhone } | Out-Null
    $code = $null
    for ($i = 0; $i -lt 40 -and -not $code; $i++) {
        Start-Sleep -Milliseconds 500
        $mm = [regex]::Match((Read-LogFrom $offset), "SIMULATED SMS to $testPhone\] Your StokVault login code is (\d{6})")
        if ($mm.Success) { $code = $mm.Groups[1].Value }
    }
    Check "one-time SMS code logs in (OTP scheme)" 204 (Send PUT "/me/password" @{ newPassword = "Lockout-2026" } $testPhone $code "OTP")
}
if ((Send GET "/me" $null $testPhone "Lockout-2026").Status -eq 200) {
    1..5 | ForEach-Object { Send GET "/me" $null $testPhone "wrong-$_" | Out-Null }
    Check "5 failed attempts lock the account (right password refused)" 401 (Send GET "/me" $null $testPhone "Lockout-2026")
} else {
    Write-Host "  SKIP  lockout (test account still locked from a recent run)" -ForegroundColor Yellow
}

Write-Host "Roles and tenant isolation"
Check "plain member can't see the platform report" 403 (Send GET "/admin/report" $null $bongani)
Check "plain member can't register groups" 403 (Send POST "/groups" @{ name = "X"; type = "GROCERY"; contributionAmount = 100; frequency = "MONTHLY"; startDate = "2026-10-01" } $bongani)
Check "plain member can't read the audit trail" 403 (Send GET "/groups/$u/audit" $null $bongani)
Check "plain member sees only their own contributions" 200 (Send GET "/groups/$u/contributions" $null $bongani) {
    param($b) @($b).Count -gt 0 -and -not (@($b) | Where-Object memberName -ne "Bongani Khumalo") }
Check "plain member sees only their own standing" 200 (Send GET "/groups/$u/summary" $null $bongani) { param($b) @($b.members).Count -eq 1 }
Check "outsider gets 404 for another stokvel (tenant isolation)" 404 (Send GET "/groups/$u" $null $zanele)
Check "outsider's group list excludes other stokvels" 200 (Send GET "/groups" $null $zanele) { param($b) -not (@($b) | Where-Object name -eq "Ubuntu Savings Club") }
Check "treasurer can't approve (committee only)" 403 (Send POST "/groups/$u/payouts/$($failedPayout.id)/approve" $null $thandi)
Check "member can't record contributions for others" 403 (Send POST "/groups/$u/contributions" @{ memberId = $bonganiId; amount = 500; paymentReference = "X-1" } $lerato)
Check "a treasurer of one group gets 404 in a group she isn't in" 404 (Send POST "/groups/$($burial.id)/cycles" $null $thandi)
Check "committee can't be removed by a plain member" 403 (Send DELETE "/groups/$u/members/$bonganiId" $null $bongani)

Write-Host "Validation"
Check "invalid SA ID number is 400" 400 (Send POST "/members" @{ fullName = "Bad Id"; nationalId = "1234567890123"; phoneNumber = "0791112222"; popiaConsent = $true } $AdminPhone $AdminPassword) {
    param($b) $b.message -like "*not a valid South African ID*" }
Check "invalid phone number is 400" 400 (Send POST "/members" @{ fullName = "Bad Phone"; nationalId = (New-SaId "910202" "5001"); phoneNumber = "12345"; popiaConsent = $true } $AdminPhone $AdminPassword)
Check "registration without POPIA consent is 400" 400 (Send POST "/members" @{ fullName = "No Consent"; nationalId = (New-SaId "920303" "5002"); phoneNumber = "0791112223"; popiaConsent = $false } $AdminPhone $AdminPassword) {
    param($b) ($b.details -join ";") -match "POPIA" }
Check "malformed JSON is 400" 400 (Send POST "/groups/$u/contributions" $null $thandi -RawBody "{not json")
Check "override needs a reason" 400 (Send POST "/groups/$u/payouts/$($failedPayout.id)/override" @{ reason = "" } $sipho)
Check "negative amount is 400" 400 (Send POST "/groups/$u/contributions" @{ amount = -5; paymentReference = "NEG" } $thandi)

Write-Host "Business rules (refused, nothing changes)"
Check "duplicate phone number is 409" 409 (Send POST "/members" @{ fullName = "Copy"; nationalId = (New-SaId "850101" "0124"); phoneNumber = "+27 82 123 4567"; popiaConsent = $true } $AdminPhone $AdminPassword)
Check "adding an existing member again is 409" 409 (Send POST "/groups/$u/members" @{ memberId = $bonganiId } $sipho)
Check "can't open a cycle while one is open" 409 (Send POST "/groups/$u/cycles" $null $thandi)
Check "can't reconcile an open cycle" 409 (Send POST "/groups/$u/cycles/$($openCycle.id)/reconcile" $null $thandi)
Check "payout that failed eligibility can't be confirmed" 409 (Send POST "/groups/$u/payouts/$($failedPayout.id)/confirm" $null $thandi) {
    param($b) $b.message -like "*eligibility check failed*" }
Check "payout must be confirmed before it's paid" 409 (Send POST "/groups/$u/payouts/$($failedPayout.id)/pay" $null $thandi)
Check "member with a payout in progress can't leave" 409 (Send DELETE "/groups/$u/members/$bonganiId" $null $thandi)
Check "treasurer can't be removed" 409 (Send DELETE "/groups/$u/members/$(($ubuntuMembers | Where-Object role -eq 'TREASURER').memberId)" $null $sipho)
Check "group with payouts in progress can't be closed" 409 (Send POST "/groups/$u/close" $null $AdminPhone $AdminPassword)
Check "an active group can't be 'resumed'" 409 (Send POST "/groups/$u/resume" $null $AdminPhone $AdminPassword)
Check "resubmitting the same payment is idempotent (200, not a duplicate)" 200 (Send POST "/groups/$u/contributions" @{
    memberId = ($ubuntuMembers | Where-Object memberName -eq "Sipho Dlamini").memberId; amount = 500; paymentReference = "UBU-C4-SIPHO" } $thandi) {
    param($b) $b.verificationStatus -eq "VERIFIED" }

Write-Host "Audit trail, reports and background jobs"
foreach ($g in $groups) {
    Check "audit chain intact: $($g.name)" 200 (Send GET "/groups/$($g.id)/audit/verify" $null $AdminPhone $AdminPassword) { param($b) $b.valid -and $b.entriesChecked -gt 10 }
}
$summary = Send GET "/groups/$u/summary" $null $thandi
Check "balance = verified contributions - paid out" 200 $summary { param($b) [decimal]$b.balance -eq ([decimal]$b.totalVerified - [decimal]$b.totalPaidOut) }
Check "arrears add up across members" 200 $summary { param($b) [decimal]$b.totalArrears -eq (@($b.members) | Measure-Object -Property arrears -Sum).Sum }
Check "group statement exports as PDF" 200 (Send GET "/groups/$u/reports/statement?format=pdf" $null $thandi) {
    param($b, $r) [Text.Encoding]::ASCII.GetString($r.Bytes, 0, 8) -eq "%PDF-1.4" }
Check "audit trail exports as CSV" 200 (Send GET "/groups/$u/reports/audit?format=csv" $null $sipho) { param($b, $r) $r.Raw -match "#,When,Action,By" }
Check "members can export their own statement" 200 (Send GET "/groups/$u/reports/members/$bonganiId/statement?format=csv" $null $bongani)
Check "but a plain member can't export someone else's" 403 (Send GET "/groups/$u/reports/members/$bonganiId/statement?format=csv" $null $naledi)
$job = Send POST "/groups/$u/payouts/eligibility-check" $null $thandi
Check "eligibility check starts as a batch job (202)" 202 $job { param($b) $b.executionId -gt 0 }
$status = $null
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Milliseconds 500
    $status = Send GET "/admin/jobs/$($job.Body.executionId)" $null $AdminPhone $AdminPassword
    if ($status.Body.status -in "COMPLETED", "FAILED") { break }
}
Check "batch job completes" 200 $status { param($b) $b.status -eq "COMPLETED" }
Check "platform report lists every group" 200 (Send GET "/admin/report" $null $AdminPhone $AdminPassword) { param($b) $b.groups -ge 3 }
Check "notifications were delivered" 200 (Send GET "/admin/notifications?status=SENT" $null $AdminPhone $AdminPassword) { param($b) @($b).Count -gt 5 }

Write-Host ""
$colour = if ($script:failed -eq 0) { "Green" } else { "Red" }
Write-Host "$script:passed passed, $script:failed failed" -ForegroundColor $colour
exit ([int]($script:failed -gt 0))
