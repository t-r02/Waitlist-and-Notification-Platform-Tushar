#Requires -Version 5.1
# End-to-end smoke test -- runs blind on a fresh checkout.
# Exits with code 1 on any failure.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$INGESTION = "http://localhost:8081"
$ADMIN     = "http://localhost:8082"
$MAILPIT   = "http://localhost:8025"

# Unique email per run
$RUN_ID = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$EMAIL  = "demo.$RUN_ID@example.com"

function Log([string]$msg) {
    Write-Host "[$([datetime]::Now.ToString('HH:mm:ss'))] $msg"
}

function Invoke-Api {
    param(
        [string]$Uri,
        [string]$Method = 'GET',
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    $params = @{
        Uri             = $Uri
        Method          = $Method
        Headers         = $Headers
        UseBasicParsing = $true
        ErrorAction     = 'Stop'
    }
    if ($Body) {
        $params.Body        = $Body
        $params.ContentType = 'application/json'
    }
    (Invoke-WebRequest @params).Content
}

Log "Test email: $EMAIL"

# -- 1. Sign up ---------------------------------------------------------------
Log "Step 1 -- POST $INGESTION/api/public/signup"
$signupBody = '{"email":"' + $EMAIL + '","name":"Demo User"}'
$signup = Invoke-Api -Uri "$INGESTION/api/public/signup" -Method POST -Body $signupBody
Log "  Response: $signup"

# -- 2. Admin login -> token --------------------------------------------------
Log "Step 2 -- POST $ADMIN/api/admin/auth/login"
$loginResp = Invoke-Api -Uri "$ADMIN/api/admin/auth/login" -Method POST `
    -Body '{"username":"admin","password":"admin123"}'
$token = ($loginResp | ConvertFrom-Json).token
if (-not $token) { Log "ERROR: login failed -- $loginResp"; exit 1 }
Log "  JWT acquired ($($token.Length) chars)"

# -- 3. Poll admin entries until Kafka propagates the signup ------------------
Log "Step 3 -- Polling $ADMIN/api/admin/entries for $EMAIL (up to 30 s)"
$entryId = $null
for ($i = 1; $i -le 15; $i++) {
    $entries = Invoke-Api -Uri "$ADMIN/api/admin/entries" `
        -Headers @{ Authorization = "Bearer $token" } | ConvertFrom-Json
    $entry = $entries | Where-Object { $_.email -eq $EMAIL } | Select-Object -First 1
    if ($entry) {
        $entryId = $entry.id
        Log "  Entry id=$entryId found (attempt $i)"
        break
    }
    Log "  Attempt ${i}: entry not yet visible -- waiting 2 s"
    Start-Sleep -Seconds 2
}
if (-not $entryId) { Log "ERROR: entry never appeared in admin-service after 30 s"; exit 1 }

# -- 4. Approve ---------------------------------------------------------------
Log "Step 4 -- PATCH $ADMIN/api/admin/entries/${entryId}?status=APPROVED"
$patchResp = Invoke-WebRequest `
    -Uri "$ADMIN/api/admin/entries/${entryId}?status=APPROVED" `
    -Method PATCH `
    -Headers @{ Authorization = "Bearer $token" } `
    -UseBasicParsing `
    -ErrorAction Stop
if ($patchResp.StatusCode -ne 200) {
    Log "ERROR: PATCH returned HTTP $($patchResp.StatusCode)"; exit 1
}
Log "  Approved (HTTP $($patchResp.StatusCode))"

# -- 5. Poll Mailpit for 2 emails ---------------------------------------------
Log "Step 5 -- Polling $MAILPIT for 2 emails to $EMAIL (up to 30 s)"
$count = 0
for ($i = 1; $i -le 15; $i++) {
    $msgs = Invoke-Api -Uri "$MAILPIT/api/v1/messages?limit=50" | ConvertFrom-Json
    $count = @($msgs.messages | Where-Object {
        $_.To -and ($_.To | Where-Object { $_.Address -eq $EMAIL })
    }).Count
    Log "  Attempt ${i}: $count email(s) received"
    if ($count -ge 2) { break }
    Start-Sleep -Seconds 2
}

if ($count -ge 2) {
    Log ""
    Log "OK -- $count emails received for $EMAIL"
    Log "  * Confirmation email (on signup)"
    Log "  * Status-update email (on APPROVED)"
    exit 0
} else {
    Log "ERROR: timed out -- only $count email(s) received for $EMAIL after 30 s"
    exit 1
}
