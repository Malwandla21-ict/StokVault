<#
.SYNOPSIS
    Fills StokVault with realistic demo data by calling the REST API.

.DESCRIPTION
    Creates two stokvels, eight members, memberships with office-bearers,
    several months of contributions (with some members behind), and rotating payouts.
    Everything goes through the API, so the business rules are applied exactly as they
    would be for a real client.

    Run it once after deploying (PowerShell, from the project folder):
        powershell -ExecutionPolicy Bypass -File scripts\seed-demo-data.ps1

    If the demo stokvels already exist it stops without changing anything.
#>
param(
    [string]$BaseUrl = "http://localhost:8081/stokvault/api"
)

$ErrorActionPreference = "Stop"

function Invoke-Api([string]$Method, [string]$Path, $Body = $null) {
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; ContentType = "application/json" }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 5) }
    try {
        return Invoke-RestMethod @params
    } catch {
        $detail = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "$Method $Path failed: $detail"
    }
}

function Get-OrCreateMember([string]$Name, [string]$Email, [string]$Phone) {
    $existing = @(Invoke-Api GET "/members?search=$([uri]::EscapeDataString($Email))") | Where-Object { $_.email -eq $Email }
    if ($existing) { return $existing[0] }
    return Invoke-Api POST "/members" @{ name = $Name; email = $Email; phone = $Phone }
}

# --- Stop if already seeded -------------------------------------------------------------
$health = Invoke-Api GET "/health"
Write-Host "API is up (database: $($health.database))"

$stokvels = @(Invoke-Api GET "/stokvels")
if ($stokvels | Where-Object { $_.name -eq "Ubuntu Savings Club" }) {
    Write-Host "Demo data already present (Ubuntu Savings Club exists). Nothing to do."
    exit 0
}

# --- Members ----------------------------------------------------------------------------
$thandi  = Get-OrCreateMember "Thandi Mokoena"  "thandi.mokoena@example.com"  "082 123 4567"
$sipho   = Get-OrCreateMember "Sipho Dlamini"   "sipho.dlamini@example.com"   "083 234 5678"
$lerato  = Get-OrCreateMember "Lerato Nkosi"    "lerato.nkosi@example.com"    "071 345 6789"
$bongani = Get-OrCreateMember "Bongani Khumalo" "bongani.khumalo@example.com" "072 456 7890"
$naledi  = Get-OrCreateMember "Naledi Mahlangu" "naledi.mahlangu@example.com" "084 567 8901"
$zanele  = Get-OrCreateMember "Zanele Ndlovu"   "zanele.ndlovu@example.com"   "076 678 9012"
$kagiso  = Get-OrCreateMember "Kagiso Molefe"   "kagiso.molefe@example.com"   "079 789 0123"
$ayanda  = Get-OrCreateMember "Ayanda Zulu"     "ayanda.zulu@example.com"     "061 890 1234"
Write-Host "Members ready: 8"

# --- Stokvel 1: a rotating stokvel, R500 a month since June -----------------------------
$ubuntu = Invoke-Api POST "/stokvels" @{
    name               = "Ubuntu Savings Club"
    description        = "Five colleagues. Each month one member receives the full pot."
    type               = "ROTATING"
    contributionAmount = 500
    frequency          = "MONTHLY"
    startDate          = "2026-06-01"
}
$u = $ubuntu.id

$roles = @(
    @($thandi,  "CHAIRPERSON"),
    @($sipho,   "TREASURER"),
    @($lerato,  "SECRETARY"),
    @($bongani, "MEMBER"),
    @($naledi,  "MEMBER")
)
foreach ($r in $roles) {
    Invoke-Api POST "/stokvels/$u/members" @{ memberId = $r[0].id; role = $r[1]; joinedOn = "2026-06-01" } | Out-Null
}

# Everyone pays R500 on the 1st of June to September, except:
#   Naledi paid only R250 in August, and Bongani missed September.
$months = "2026-06-01", "2026-07-01", "2026-08-01", "2026-09-01"
foreach ($date in $months) {
    foreach ($m in $thandi, $sipho, $lerato, $bongani, $naledi) {
        $amount = 500
        if ($m.id -eq $naledi.id -and $date -eq "2026-08-01") { $amount = 250 }
        if ($m.id -eq $bongani.id -and $date -eq "2026-09-01") { continue }
        $method = if ($m.id -eq $thandi.id) { "DEBIT_ORDER" } elseif ($m.id -eq $lerato.id) { "CASH" } else { "EFT" }
        Invoke-Api POST "/stokvels/$u/contributions" @{
            memberId = $m.id; amount = $amount; contributionDate = $date; paymentMethod = $method
            reference = "UBU-$($date.Substring(0, 7))-$($m.id)"
        } | Out-Null
    }
}
Write-Host "Ubuntu Savings Club: 5 members, 19 contributions"

# June, July and August pots are paid out in rotation order, using the API's /next suggestion.
foreach ($date in "2026-06-25", "2026-07-25", "2026-08-25") {
    $next = Invoke-Api GET "/stokvels/$u/payouts/next"
    $payout = Invoke-Api POST "/stokvels/$u/payouts" @{
        memberId = $next.memberId; amount = $next.suggestedAmount; payoutDate = $date
        notes = "Rotation payout, position $($next.payoutPosition)"
    }
    Invoke-Api POST "/stokvels/$u/payouts/$($payout.id)/pay" | Out-Null
    Write-Host "  Paid $($next.memberName) R$($next.suggestedAmount) ($date)"
}

# September's payout is scheduled but not paid: the pot is short because of the arrears.
$next = Invoke-Api GET "/stokvels/$u/payouts/next"
Invoke-Api POST "/stokvels/$u/payouts" @{
    memberId = $next.memberId; amount = $next.suggestedAmount; payoutDate = "2026-09-30"
    notes = "September rotation - waiting for outstanding contributions"
} | Out-Null
Write-Host "  Scheduled September payout for $($next.memberName)"

# --- Stokvel 2: a grocery stokvel, R300 a month since January ---------------------------
$grocery = Invoke-Api POST "/stokvels" @{
    name               = "Kasi December Grocery"
    description        = "Bulk grocery shopping together in December."
    type               = "GROCERY"
    contributionAmount = 300
    frequency          = "MONTHLY"
    startDate          = "2026-01-01"
}
$g = $grocery.id
Invoke-Api POST "/stokvels/$g/members" @{ memberId = $zanele.id; role = "CHAIRPERSON"; joinedOn = "2026-01-01" } | Out-Null
Invoke-Api POST "/stokvels/$g/members" @{ memberId = $kagiso.id; role = "TREASURER"; joinedOn = "2026-01-01" } | Out-Null
Invoke-Api POST "/stokvels/$g/members" @{ memberId = $ayanda.id; joinedOn = "2026-01-01" } | Out-Null
# Thandi is in both stokvels; she joined this one in April
Invoke-Api POST "/stokvels/$g/members" @{ memberId = $thandi.id; joinedOn = "2026-04-01" } | Out-Null

$count = 0
for ($month = 1; $month -le 9; $month++) {
    $date = "2026-{0:D2}-05" -f $month
    foreach ($m in $zanele, $kagiso, $ayanda, $thandi) {
        if ($m.id -eq $thandi.id -and $month -lt 4) { continue }   # not a member yet
        if ($m.id -eq $ayanda.id -and $month -ge 8) { continue }   # Ayanda is 2 months behind
        Invoke-Api POST "/stokvels/$g/contributions" @{
            memberId = $m.id; amount = 300; contributionDate = $date; paymentMethod = "EFT"
        } | Out-Null
        $count++
    }
}
Write-Host "Kasi December Grocery: 4 members, $count contributions"

# --- Summary ----------------------------------------------------------------------------
foreach ($id in $u, $g) {
    $s = Invoke-Api GET "/stokvels/$id/summary"
    Write-Host ""
    Write-Host "$($s.name): balance R$($s.balance), paid out R$($s.totalPaidOut), arrears R$($s.totalArrears)"
    $s.members | ForEach-Object {
        Write-Host ("  {0,-18} paid R{1,-8} expected R{2,-8} arrears R{3}" -f $_.name, $_.totalContributed, $_.expectedToDate, $_.arrears)
    }
}
Write-Host ""
Write-Host "Done. Open http://localhost:8081/stokvault/ to see it in the dashboard."
