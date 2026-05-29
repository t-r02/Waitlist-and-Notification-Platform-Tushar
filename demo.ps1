#Requires -Version 5.1
# End-to-end smoke test. Exits with code 1 on any failure.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$INGESTION = "http://localhost:8081"
$ADMIN     = "http://localhost:8082"
$MAILPIT   = "http://localhost:8025"

$RUN_ID = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$EMAIL  = "demo.$RUN_ID@example.com"

function Log([string]$msg) {
    Write-Host "[$([datetime]::Now.ToString('HH:mm:ss'))] $msg"
}
function Die([string]$msg) { Log "ERROR: $msg"; exit 1 }

function Invoke-Api {
    param(
        [string]$Uri,
        [string]$Method   = 'GET',
        [hashtable]$Headers = @{},
        [string]$Body     = $null
    )
    $p = @{ Uri = $Uri; Method = $Method; Headers = $Headers; UseBasicParsing = $true; ErrorAction = 'Stop' }
    if ($Body) { $p.Body = $Body; $p.ContentType = 'application/json' }
    (Invoke-WebRequest @p).Content
}

Log "Run email: $EMAIL"

# -- 1. Sign up ---------------------------------------------------------------
Log "1/6  Sign up"
$signupJson = Invoke-Api -Uri "$INGESTION/api/public/signup" -Method POST `
    -Body ('{"email":"' + $EMAIL + '","name":"Demo User"}')
$signup = $signupJson | ConvertFrom-Json
Log "     verified=$($signup.verified)  referralCode=$($signup.referralCode)"
if ($signup.verified)        { Die "new signup should be unverified" }
if ($signup.referralCode)    { Die "referral code must be hidden before verification" }

# -- 2. Extract verification token from Mailpit -------------------------------
Log "2/6  Waiting for verification email (up to 20 s)"
$vToken = $null
for ($i = 1; $i -le 10; $i++) {
    $msgs = (Invoke-Api -Uri "$MAILPIT/api/v1/messages?limit=20") | ConvertFrom-Json
    foreach ($m in $msgs.messages) {
        $toAddrs = @($m.To | ForEach-Object { $_.Address })
        if ($toAddrs -contains $EMAIL -and $m.Subject -match 'Verify') {
            if ($m.Snippet -match 'token=([a-f0-9]{64})') {
                $vToken = $Matches[1]
                break
            }
        }
    }
    if ($vToken) { Log "     Token captured (attempt $i)"; break }
    Log "     Attempt ${i}: email not yet in Mailpit -- waiting 2 s"
    Start-Sleep -Seconds 2
}
if (-not $vToken) { Die "verification email never arrived after 20 s" }

# -- 3. Verify email ----------------------------------------------------------
Log "3/6  Verify email"
$verifyJson = Invoke-Api -Uri "$INGESTION/api/public/verify?token=$vToken" -Method POST
$verify     = $verifyJson | ConvertFrom-Json
Log "     Referral code: $($verify.referralCode)"
if (-not $verify.referralCode) { Die "referral code should be returned after verification" }

# -- 4. Admin login -----------------------------------------------------------
Log "4/6  Admin login"
$loginResp = Invoke-Api -Uri "$ADMIN/api/admin/auth/login" -Method POST `
    -Body '{"username":"admin","password":"admin123"}'
$token = ($loginResp | ConvertFrom-Json).token
if (-not $token) { Die "admin login failed" }
Log "     JWT acquired"

# -- 5. Poll admin entries until the verified signup propagates ---------------
Log "5/6  Waiting for entry to appear in admin dashboard (up to 30 s)"
$entryId = $null
for ($i = 1; $i -le 15; $i++) {
    $entries = (Invoke-Api -Uri "$ADMIN/api/admin/entries" `
        -Headers @{ Authorization = "Bearer $token" }) | ConvertFrom-Json
    $found = $entries | Where-Object { $_.email -eq $EMAIL } | Select-Object -First 1
    if ($found) { $entryId = $found.id; Log "     Entry id=$entryId (attempt $i)"; break }
    Log "     Attempt ${i}: not yet visible -- waiting 2 s"
    Start-Sleep -Seconds 2
}
if (-not $entryId) { Die "entry never appeared in admin service after 30 s" }

$patchResp = Invoke-WebRequest `
    -Uri "$ADMIN/api/admin/entries/${entryId}?status=APPROVED" `
    -Method PATCH `
    -Headers @{ Authorization = "Bearer $token" } `
    -UseBasicParsing -ErrorAction Stop
if ($patchResp.StatusCode -ne 200) { Die "PATCH returned HTTP $($patchResp.StatusCode)" }
Log "     Approved"

# -- 6. Confirm three emails arrived ------------------------------------------
Log "6/6  Polling Mailpit for 3 emails (verify + welcome + status) up to 30 s"
$count = 0
for ($i = 1; $i -le 15; $i++) {
    $msgs  = (Invoke-Api -Uri "$MAILPIT/api/v1/messages?limit=50") | ConvertFrom-Json
    $count = @($msgs.messages | Where-Object {
        $_.To -and ($_.To | Where-Object { $_.Address -eq $EMAIL })
    }).Count
    Log "     Attempt ${i}: $count email(s) received"
    if ($count -ge 3) { break }
    Start-Sleep -Seconds 2
}

if ($count -ge 3) {
    Log ""
    Log "OK -- $count emails confirmed for $EMAIL"
    Log "  1. Verification link        (ingestion-service, on signup)"
    Log "  2. Welcome + referral code  (notification-service, after email verified)"
    Log "  3. Status update            (notification-service, on APPROVED)"
    exit 0
} else {
    Die "timed out -- only $count email(s) received (expected 3)"
}
