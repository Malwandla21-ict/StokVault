<#
.SYNOPSIS
    End-to-end checks of the deployed StokVault API: status codes, validation and business rules.

.DESCRIPTION
    Needs the demo data (run seed-demo-data.ps1 first). The rule checks are requests the API
    must refuse, so they change nothing; the few records the script creates for the
    create/update/delete checks are deleted again at the end.

        powershell -ExecutionPolicy Bypass -File scripts\api-tests.ps1

    Exit code 0 = all passed, 1 = something failed.
#>
param(
    [string]$BaseUrl = "http://localhost:8081/stokvault/api"
)

$script:passed = 0
$script:failed = 0

# Sends a request and returns @{ Status = 200; Body = <parsed JSON or $null> } without throwing on 4xx/5xx
function Send([string]$Method, [string]$Path, $Body = $null, [string]$RawBody = $null) {
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; ContentType = "application/json"; UseBasicParsing = $true }
    if ($null -ne $Body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 5)) }
    if ($RawBody) { $params.Body = $RawBody }
    try {
        $r = Invoke-WebRequest @params
        $status = [int]$r.StatusCode
        $content = $r.Content
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { throw }
        $status = [int]$resp.StatusCode
        $content = $_.ErrorDetails.Message
    }
    $parsed = $null
    if ($content) { try { $parsed = $content | ConvertFrom-Json } catch { } }
    return @{ Status = $status; Body = $parsed; Raw = $content }
}

function Check([string]$Name, [int]$Expected, $Response, [scriptblock]$Extra = $null) {
    $ok = $Response.Status -eq $Expected
    if ($ok -and $Extra) { $ok = [bool](& $Extra $Response.Body) }
    if ($ok) {
        $script:passed++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host "  FAIL  $Name (expected $Expected, got $($Response.Status)) $($Response.Raw)" -ForegroundColor Red
    }
}

# --- Find the demo data -----------------------------------------------------------------
$stokvels = (Send GET "/stokvels").Body
$ubuntu = $stokvels | Where-Object name -eq "Ubuntu Savings Club"
$grocery = $stokvels | Where-Object name -eq "Kasi December Grocery"
if (-not $ubuntu -or -not $grocery) {
    Write-Host "Demo data not found. Run scripts\seed-demo-data.ps1 first." -ForegroundColor Yellow
    exit 1
}
$u = $ubuntu.id
$g = $grocery.id
$ubuntuMembers = (Send GET "/stokvels/$u/members").Body
$thandi = $ubuntuMembers | Where-Object memberName -eq "Thandi Mokoena"
$bongani = $ubuntuMembers | Where-Object memberName -eq "Bongani Khumalo"
$scheduled = @((Send GET "/stokvels/$u/payouts?status=SCHEDULED").Body)[0]

Write-Host "Health and errors"
Check "health reports database up" 200 (Send GET "/health") { param($b) $b.database -eq "up" }
Check "unknown URL is a JSON 404" 404 (Send GET "/no-such-thing") { param($b) $b.status -eq 404 }
Check "unknown member is 404 with a message" 404 (Send GET "/members/999999") { param($b) $b.message -like "*not found*" }
Check "malformed JSON is 400" 400 (Send POST "/members" -RawBody "{not json")
Check "wrong HTTP method is 405" 405 (Send DELETE "/stokvels")

Write-Host "Validation"
Check "blank name and bad email are rejected with details" 400 (Send POST "/members" @{ name = ""; email = "nope" }) {
    param($b) ($b.details -join ";") -match "name:" -and ($b.details -join ";") -match "email:" }
Check "bad phone number is rejected" 400 (Send POST "/members" @{ name = "X"; email = "x@example.com"; phone = "abc" })
Check "negative contribution is rejected" 400 (Send POST "/stokvels/$u/contributions" @{ memberId = $thandi.memberId; amount = -5 })
Check "more than 2 decimal places is rejected" 400 (Send POST "/stokvels/$u/contributions" @{ memberId = $thandi.memberId; amount = 10.555 })
Check "future-dated contribution is rejected" 400 (Send POST "/stokvels/$u/contributions" @{ memberId = $thandi.memberId; amount = 100; contributionDate = "2099-01-01" })
Check "unknown enum value is 400" 400 (Send POST "/stokvels" @{ name = "Bad"; type = "LOTTERY"; contributionAmount = 100; frequency = "MONTHLY"; startDate = "2026-01-01" })
Check "invalid date in query string is 400" 400 (Send GET "/stokvels/$u/contributions?from=yesterday")

Write-Host "Business rules (all refused, nothing changes)"
Check "duplicate email is 409" 409 (Send POST "/members" @{ name = "Copy"; email = "THANDI.MOKOENA@example.com" })
Check "duplicate stokvel name is 409" 409 (Send POST "/stokvels" @{ name = "ubuntu savings club"; type = "SAVINGS"; contributionAmount = 100; frequency = "MONTHLY"; startDate = "2026-01-01" })
Check "joining the same stokvel twice is 409" 409 (Send POST "/stokvels/$u/members" @{ memberId = $thandi.memberId })
Check "second chairperson is 409" 409 (Send PUT "/stokvels/$u/members/$($bongani.memberId)" @{ role = "CHAIRPERSON" })
Check "contribution before the stokvel started is 409" 409 (Send POST "/stokvels/$u/contributions" @{ memberId = $thandi.memberId; amount = 500; contributionDate = "2026-05-01" })
Check "non-member can't contribute (404)" 404 (Send POST "/stokvels/$g/contributions" @{ memberId = $bongani.memberId; amount = 300 })
Check "paying more than the balance is 409 (insufficient funds)" 409 (Send POST "/stokvels/$u/payouts/$($scheduled.id)/pay") {
    param($b) $b.message -like "Insufficient funds*" }
Check "rotation on a non-rotating stokvel is 409" 409 (Send GET "/stokvels/$g/payouts/next")
Check "closing with a scheduled payout is 409" 409 (Send POST "/stokvels/$u/close")
Check "deleting a stokvel with history is 409" 409 (Send DELETE "/stokvels/$u")
Check "deleting a member who belongs to a stokvel is 409" 409 (Send DELETE "/members/$($thandi.memberId)")
Check "member with a scheduled payout can't leave" 409 (Send DELETE "/stokvels/$u/members/$($scheduled.memberId)")

Write-Host "Reports"
$summary = (Send GET "/stokvels/$u/summary")
Check "summary balance = contributions - paid out" 200 $summary {
    param($b) [decimal]$b.balance -eq ([decimal]$b.totalContributions - [decimal]$b.totalPaidOut) }
Check "summary arrears add up" 200 $summary {
    param($b) [decimal]$b.totalArrears -eq ($b.members | Measure-Object -Property arrears -Sum).Sum }
# Positions 1-3 were paid and position 4 has a scheduled payout (it counts as their turn), so it's position 5
Check "next payout skips members already paid or scheduled" 200 (Send GET "/stokvels/$u/payouts/next") {
    param($b) $b.payoutPosition -eq 5 -and $b.payoutsAlreadyReceived -eq 0 -and [decimal]$b.suggestedAmount -eq 2500 }
Check "contributions can be filtered by member and date" 200 (Send GET "/stokvels/$u/contributions?memberId=$($thandi.memberId)&from=2026-07-01&to=2026-08-31") {
    param($b) @($b).Count -eq 2 }

Write-Host "Create, update, delete (cleaned up afterwards)"
$stamp = Get-Date -Format "HHmmss"
$m = Send POST "/members" @{ name = "Test Person $stamp"; email = "test.$stamp@example.com" }
Check "create member is 201 with Location" 201 $m { param($b) $b.id -gt 0 }
Check "update member" 200 (Send PUT "/members/$($m.Body.id)" @{ name = "Renamed $stamp"; email = "test.$stamp@example.com"; phone = "0820000000" }) {
    param($b) $b.name -eq "Renamed $stamp" -and $b.phone -eq "0820000000" }
$s = Send POST "/stokvels" @{ name = "Test Stokvel $stamp"; type = "SAVINGS"; contributionAmount = 250; frequency = "WEEKLY"; startDate = "2026-09-01" }
Check "create stokvel is 201, amount has 2 decimals" 201 $s { param($b) $b.contributionAmount -eq 250 -and $b.status -eq "ACTIVE" }
Check "add member to stokvel" 201 (Send POST "/stokvels/$($s.Body.id)/members" @{ memberId = $m.Body.id; role = "TREASURER" }) {
    param($b) $b.payoutPosition -eq 1 -and $b.role -eq "TREASURER" }
Check "member leaves stokvel" 200 (Send DELETE "/stokvels/$($s.Body.id)/members/$($m.Body.id)") { param($b) -not $b.active }
Check "empty stokvel can be deleted" 204 (Send DELETE "/stokvels/$($s.Body.id)")
Check "member with no stokvels can be deleted" 204 (Send DELETE "/members/$($m.Body.id)")
Check "deleted member is gone" 404 (Send GET "/members/$($m.Body.id)")

Write-Host ""
$colour = if ($script:failed -eq 0) { "Green" } else { "Red" }
Write-Host "$script:passed passed, $script:failed failed" -ForegroundColor $colour
exit ([int]($script:failed -gt 0))
