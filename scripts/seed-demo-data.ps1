<#
.SYNOPSIS
    Loads realistic demo data into StokVault through the REST API.

.DESCRIPTION
    Creates three stokvels that show every part of the system:

      Ubuntu Savings Club     ROTATIONAL, R500/month since June. Cycles 1-3 were paid out in
                              rotation (each above the R2000 approval threshold, so a committee
                              member approved them). Cycle 4 is open: one member hasn't paid,
                              one paid short (flagged for review), one self-reported (pending),
                              and its payout has FAILED the automated eligibility check.
      Kasi December Grocery   GROCERY, R300/month since January; one member is two months behind.
      Siyakhana Burial Society BURIAL, R150/month since July, with a funeral claim that passed
                              the eligibility check and is confirmed, ready to pay.

    Everything goes through the API, so all the business rules apply. New members sign in the
    first time with a one-time SMS code; the (simulated) SMS gateway writes codes to Payara's
    server.log, which this script reads. Each demo member then sets the password below.

    DEMO ONLY: every demo member's password is Demo-2026.

    Run from the project folder (Payara must be running with StokVault deployed):
        powershell -ExecutionPolicy Bypass -File scripts\seed-demo-data.ps1
    The admin password is read from server.log (where it was printed on first start), or pass
    -AdminPassword. The script stops if the demo data already exists.
#>
param(
    [string]$BaseUrl = "http://localhost:8081/stokvault/api",
    [string]$ServerLog = "$env:USERPROFILE\OneDrive\Documents\payara7\glassfish\domains\domain1\logs\server.log",
    [string]$AdminPhone = "0600000000",
    [string]$AdminPassword
)

$ErrorActionPreference = "Stop"
$DemoPassword = "Demo-2026"

# ---- helpers ------------------------------------------------------------------------------

function Read-LogFrom([long]$offset) {
    $fs = [IO.File]::Open($ServerLog, 'Open', 'Read', 'ReadWrite')
    try {
        $fs.Seek($offset, 'Begin') | Out-Null
        return (New-Object IO.StreamReader($fs)).ReadToEnd()
    } finally { $fs.Close() }
}

function Get-AuthHeader($cred) {
    $token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$($cred.Phone):$($cred.Secret)"))
    return "$($cred.Scheme) $token"
}

function Invoke-Api([string]$Method, [string]$Path, $Body = $null, $Cred = $null) {
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; ContentType = "application/json"; Headers = @{} }
    if ($Cred) { $params.Headers.Authorization = Get-AuthHeader $Cred }
    if ($null -ne $Body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6)) }
    try {
        return Invoke-RestMethod @params
    } catch {
        $detail = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "$Method $Path failed: $detail"
    }
}

function Cred([string]$phone, [string]$secret = $DemoPassword) {
    return @{ Phone = $phone; Secret = $secret; Scheme = "Basic" }
}

# A valid South African ID number: YYMMDD + 4-digit sequence + citizenship 0 + 8 + Luhn check digit
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

# Registers a member (as admin) and gives them the demo password via a one-time SMS code
function New-DemoMember([string]$name, [string]$phone, [string]$birth, [string]$seq, [string]$email, [string]$channel = "SMS") {
    $member = @(Invoke-Api GET "/members?q=$phone" $null $script:admin) | Select-Object -First 1
    if (-not $member) {
        $member = Invoke-Api POST "/members" @{
            fullName = $name; nationalId = (New-SaId $birth $seq); phoneNumber = $phone
            email = $email; preferredChannel = $channel; popiaConsent = $true
        } $script:admin
    }
    if ($member.passwordSet) { return $member }

    $offset = (Get-Item $ServerLog).Length
    Invoke-Api POST "/auth/login-code" @{ phoneNumber = $phone } | Out-Null
    $code = $null
    for ($i = 0; $i -lt 40 -and -not $code; $i++) {
        Start-Sleep -Milliseconds 500
        $m = [regex]::Match((Read-LogFrom $offset), "SIMULATED (SMS|WHATSAPP) to $phone\] Your StokVault login code is (\d{6})")
        if ($m.Success) { $code = $m.Groups[2].Value }
    }
    if (-not $code) { throw "No login code for $phone appeared in $ServerLog" }
    Invoke-Api PUT "/me/password" @{ newPassword = $DemoPassword } @{ Phone = $phone; Secret = $code; Scheme = "OTP" } | Out-Null
    return $member
}

function Add-ToGroup($group, $member, [string]$role, [string]$joined) {
    Invoke-Api POST "/groups/$($group.id)/members" @{ memberId = $member.id; role = $role; joinedDate = $joined } $script:admin | Out-Null
}

function Open-Cycle($group, $treasurer, [string]$due) {
    return Invoke-Api POST "/groups/$($group.id)/cycles?dueDate=$due" $null $treasurer
}

function Pay-In($group, $cycle, $recorder, $member, [decimal]$amount, [string]$ref, [string]$date, [string]$method = "EFT") {
    $body = @{ cycleId = $cycle.id; amount = $amount; paymentReference = $ref; paymentMethod = $method; contributionDate = $date }
    if ($member) { $body.memberId = $member.id }
    return Invoke-Api POST "/groups/$($group.id)/contributions" $body $recorder
}

function Verify($group, $contribution, $verifier) {
    Invoke-Api POST "/groups/$($group.id)/contributions/$($contribution.id)/verify" @{ decision = "VERIFIED" } $verifier | Out-Null
}

# The eligibility check runs as a background batch job; wait for its result
function Wait-Eligibility($group, $payout, $cred) {
    for ($i = 0; $i -lt 60; $i++) {
        $p = Invoke-Api GET "/groups/$($group.id)/payouts/$($payout.id)" $null $cred
        if ($p.eligibilityCheck -ne "NOT_RUN") { return $p }
        Start-Sleep -Milliseconds 500
    }
    throw "The eligibility check for payout $($payout.id) did not finish"
}

# ---- start ----------------------------------------------------------------------------------

if (-not $AdminPassword) {
    $m = [regex]::Matches((Read-LogFrom 0), "one-time generated password: (\S+)\.")
    if ($m.Count -eq 0) { throw "Pass -AdminPassword (the admin password isn't in $ServerLog)" }
    $AdminPassword = $m[$m.Count - 1].Groups[1].Value
}
$script:admin = Cred $AdminPhone $AdminPassword

$health = Invoke-Api GET "/health"
Write-Host "API is up (database: $($health.database))"
$existing = @(Invoke-Api GET "/groups" $null $script:admin)
if ($existing | Where-Object { $_.name -eq "Ubuntu Savings Club" }) {
    Write-Host "Demo data already present. Nothing to do."
    exit 0
}

Write-Host "Registering members (each signs in once with an SMS code and sets a password)..."
$thandi  = New-DemoMember "Thandi Mokoena"  "0821234567" "850314" "0123" "thandi.mokoena@example.com"
$sipho   = New-DemoMember "Sipho Dlamini"   "0832345678" "791102" "5234" "sipho.dlamini@example.com"
$lerato  = New-DemoMember "Lerato Nkosi"    "0713456789" "900721" "0345" $null
$bongani = New-DemoMember "Bongani Khumalo" "0724567890" "880130" "5456" $null
$naledi  = New-DemoMember "Naledi Mahlangu" "0845678901" "950509" "0567" "naledi.mahlangu@example.com" "WHATSAPP"
$zanele  = New-DemoMember "Zanele Ndlovu"   "0766789012" "821212" "0678" "zanele.ndlovu@example.com"
$kagiso  = New-DemoMember "Kagiso Molefe"   "0797890123" "920917" "5789" $null
$ayanda  = New-DemoMember "Ayanda Zulu"     "0618901234" "980425" "5890" $null
Write-Host "  8 members registered"

# ---- Ubuntu Savings Club: rotational --------------------------------------------------------
$ubuntu = Invoke-Api POST "/groups" @{
    name = "Ubuntu Savings Club"; type = "ROTATIONAL"; contributionAmount = 500; frequency = "MONTHLY"
    startDate = "2026-06-01"; approvalThreshold = 2000; completionThreshold = 100
    description = "Five colleagues. Each month one member receives the whole pot."
} $script:admin
Add-ToGroup $ubuntu $thandi  "TREASURER" "2026-06-01"
Add-ToGroup $ubuntu $sipho   "COMMITTEE" "2026-06-01"
Add-ToGroup $ubuntu $lerato  "COMMITTEE" "2026-06-01"
Add-ToGroup $ubuntu $bongani "MEMBER"    "2026-06-01"
Add-ToGroup $ubuntu $naledi  "MEMBER"    "2026-06-01"
Invoke-Api POST "/groups/$($ubuntu.id)/activate" $null $script:admin | Out-Null

$tThandi = Cred "0821234567"; $cSipho = Cred "0832345678"; $cLerato = Cred "0713456789"
$months = @(@{ n = 1; due = "2026-06-01"; paid = "2026-06-01" }, @{ n = 2; due = "2026-07-01"; paid = "2026-07-01" }, @{ n = 3; due = "2026-08-01"; paid = "2026-08-01" })
foreach ($mo in $months) {
    $cycle = Open-Cycle $ubuntu $tThandi $mo.due
    # The treasurer's own payment is self-reported, so a committee member verifies it
    $own = Pay-In $ubuntu $cycle $tThandi $null 500 "UBU-C$($mo.n)-THANDI" $mo.paid "DEBIT_ORDER"
    Verify $ubuntu $own $cSipho
    foreach ($p in @(@($sipho, "SIPHO"), @($lerato, "LERATO"), @($bongani, "BONGANI"), @($naledi, "NALEDI"))) {
        Pay-In $ubuntu $cycle $tThandi $p[0] 500 "UBU-C$($mo.n)-$($p[1])" $mo.paid | Out-Null
    }
    Invoke-Api POST "/groups/$($ubuntu.id)/cycles/$($cycle.id)/close" $null $tThandi | Out-Null

    $payout = @(Invoke-Api POST "/groups/$($ubuntu.id)/payouts/run" @{ cycleId = $cycle.id; payoutDate = $mo.due.Replace("-01", "-25") } $tThandi)[0]
    $payout = Wait-Eligibility $ubuntu $payout $tThandi
    Invoke-Api POST "/groups/$($ubuntu.id)/payouts/$($payout.id)/confirm" $null $tThandi | Out-Null   # R2500 > R2000: needs approval
    # Four-eyes: a committee member approves, but never one approving a payout to themselves
    $approver = if ($payout.memberId -eq $sipho.id) { $cLerato } else { $cSipho }
    Invoke-Api POST "/groups/$($ubuntu.id)/payouts/$($payout.id)/approve" $null $approver | Out-Null
    Invoke-Api POST "/groups/$($ubuntu.id)/payouts/$($payout.id)/pay" $null $tThandi | Out-Null
    Write-Host "  Ubuntu cycle $($mo.n): paid R$($payout.amount) to $($payout.memberName) (eligibility $($payout.eligibilityCheck), approved by committee)"
}

# Cycle 4 (September) is still open and incomplete
$c4 = Open-Cycle $ubuntu $tThandi "2026-09-01"
$own = Pay-In $ubuntu $c4 $tThandi $null 500 "UBU-C4-THANDI" "2026-09-01" "DEBIT_ORDER"
Verify $ubuntu $own $cSipho
Pay-In $ubuntu $c4 $tThandi $sipho 500 "UBU-C4-SIPHO" "2026-09-01" | Out-Null
Pay-In $ubuntu $c4 $cLerato $null 500 "FNB-99812-LERATO" "2026-09-03" | Out-Null              # self-reported: PENDING
Pay-In $ubuntu $c4 $tThandi $naledi 250 "UBU-C4-NALEDI" "2026-09-05" "CASH" | Out-Null        # short: PENDING_REVIEW
# Bongani hasn't paid. The treasurer runs the payout anyway: the automated check fails it.
$failed = @(Invoke-Api POST "/groups/$($ubuntu.id)/payouts/run" @{ cycleId = $c4.id; payoutDate = "2026-09-25" } $tThandi)[0]
$failed = Wait-Eligibility $ubuntu $failed $tThandi
Write-Host "  Ubuntu cycle 4: open; payout to $($failed.memberName) is $($failed.eligibilityCheck): $($failed.eligibilityNotes)"

# ---- Kasi December Grocery -----------------------------------------------------------------
$grocery = Invoke-Api POST "/groups" @{
    name = "Kasi December Grocery"; type = "GROCERY"; contributionAmount = 300; frequency = "MONTHLY"
    startDate = "2026-01-01"; description = "Bulk grocery shopping together in December."
} $script:admin
Add-ToGroup $grocery $zanele "TREASURER" "2026-01-01"
Add-ToGroup $grocery $kagiso "COMMITTEE" "2026-01-01"
Add-ToGroup $grocery $ayanda "MEMBER"    "2026-01-01"
Add-ToGroup $grocery $thandi "MEMBER"    "2026-04-01"
Invoke-Api POST "/groups/$($grocery.id)/activate" $null $script:admin | Out-Null
$tZanele = Cred "0766789012"; $cKagiso = Cred "0797890123"
for ($n = 1; $n -le 9; $n++) {
    $due = "2026-{0:D2}-01" -f $n
    $paid = "2026-{0:D2}-05" -f $n
    $cycle = Open-Cycle $grocery $tZanele $due
    $own = Pay-In $grocery $cycle $tZanele $null 300 "KDG-$n-ZANELE" $paid
    Verify $grocery $own $cKagiso
    Pay-In $grocery $cycle $tZanele $kagiso 300 "KDG-$n-KAGISO" $paid | Out-Null
    if ($n -lt 8) { Pay-In $grocery $cycle $tZanele $ayanda 300 "KDG-$n-AYANDA" $paid "CASH" | Out-Null }   # Ayanda is 2 months behind
    if ($n -ge 4) { Pay-In $grocery $cycle $tZanele $thandi 300 "KDG-$n-THANDI" $paid | Out-Null }         # joined in April
    if ($n -lt 9) {
        Invoke-Api POST "/groups/$($grocery.id)/cycles/$($cycle.id)/close" $null $tZanele | Out-Null
        Invoke-Api POST "/groups/$($grocery.id)/cycles/$($cycle.id)/reconcile" $null $tZanele | Out-Null
    }
}
Write-Host "  Kasi December Grocery: 9 cycles, cycle 9 open"

# ---- Siyakhana Burial Society ----------------------------------------------------------------
$burial = Invoke-Api POST "/groups" @{
    name = "Siyakhana Burial Society"; type = "BURIAL"; contributionAmount = 150; frequency = "MONTHLY"
    startDate = "2026-07-01"; benefitAmount = 1500; approvalThreshold = 2000
    description = "Pays R1500 to a member's household towards funeral costs."
} $script:admin
Add-ToGroup $burial $sipho   "TREASURER" "2026-07-01"
Add-ToGroup $burial $naledi  "COMMITTEE" "2026-07-01"
Add-ToGroup $burial $kagiso  "MEMBER"    "2026-07-01"
Add-ToGroup $burial $ayanda  "MEMBER"    "2026-07-01"
Add-ToGroup $burial $bongani "MEMBER"    "2026-07-01"
Invoke-Api POST "/groups/$($burial.id)/activate" $null $script:admin | Out-Null
$tSipho = Cred "0832345678"; $cNaledi = Cred "0845678901"
for ($n = 1; $n -le 3; $n++) {
    $due = "2026-{0:D2}-01" -f ($n + 6)
    $cycle = Open-Cycle $burial $tSipho $due
    $own = Pay-In $burial $cycle $tSipho $null 150 "SBS-$n-SIPHO" $due
    Verify $burial $own $cNaledi
    foreach ($p in @(@($naledi, "NALEDI"), @($kagiso, "KAGISO"), @($ayanda, "AYANDA"), @($bongani, "BONGANI"))) {
        Pay-In $burial $cycle $tSipho $p[0] 150 "SBS-$n-$($p[1])" $due | Out-Null
    }
    if ($n -lt 3) { Invoke-Api POST "/groups/$($burial.id)/cycles/$($cycle.id)/close" $null $tSipho | Out-Null }
}
$claim = @(Invoke-Api POST "/groups/$($burial.id)/payouts/run" @{ beneficiaryMemberId = $bongani.id; notes = "Funeral of Bongani's mother" } $tSipho)[0]
$claim = Wait-Eligibility $burial $claim $tSipho
Invoke-Api POST "/groups/$($burial.id)/payouts/$($claim.id)/confirm" $null $tSipho | Out-Null
Write-Host "  Siyakhana Burial Society: claim of R$($claim.amount) for $($claim.memberName) is $($claim.eligibilityCheck) and confirmed"

# ---- summary --------------------------------------------------------------------------------
Write-Host ""
foreach ($g in $ubuntu, $grocery, $burial) {
    $s = Invoke-Api GET "/groups/$($g.id)/summary" $null $script:admin
    $chain = Invoke-Api GET "/groups/$($g.id)/audit/verify" $null $script:admin
    Write-Host ("{0}: balance R{1}, arrears R{2}, awaiting verification {3}, audit chain {4} ({5} entries)" -f `
        $s.group.name, $s.balance, $s.totalArrears, $s.awaitingVerification, $(if ($chain.valid) { "intact" } else { "BROKEN" }), $chain.entriesChecked)
}
Write-Host ""
Write-Host "Done. Log in at http://localhost:8081/stokvault/ - demo members use their phone number and password $DemoPassword"
Write-Host "  Treasurer: 082 123 4567 (Thandi)   Committee: 083 234 5678 (Sipho)   Member: 072 456 7890 (Bongani)"
