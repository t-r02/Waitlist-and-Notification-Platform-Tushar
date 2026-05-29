#Requires -Version 5.1
# Bring up the full waitlist platform and wait until all services report healthy.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

docker compose up -d --build

Write-Host "Waiting for services to become healthy..."

$urls = @(
    "http://localhost:8081/actuator/health",
    "http://localhost:8082/actuator/health",
    "http://localhost:8083/actuator/health"
)

foreach ($url in $urls) {
    Write-Host -NoNewline "  $url ... "
    $ready = $false
    for ($i = 1; $i -le 60; $i++) {
        try {
            Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null
            $ready = $true
            break
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    if ($ready) {
        Write-Host "UP"
    } else {
        Write-Host "TIMEOUT"
        Write-Error "ERROR: $url did not become healthy within 120 s"
        exit 1
    }
}

Write-Host -NoNewline "  http://localhost:8080 ... "
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        Invoke-WebRequest -Uri "http://localhost:8080" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if ($ready) {
    Write-Host "UP"
} else {
    Write-Host "TIMEOUT"
    Write-Error "ERROR: http://localhost:8080 did not become ready within 120 s"
    exit 1
}

Write-Host ""
Write-Host "All services are up."
Write-Host ""
Write-Host "  App pages:"
Write-Host "    Sign up / Leaderboard  : http://localhost:8080"
Write-Host "    Email verify (link)    : http://localhost:8080/verify?token=<token>"
Write-Host "    Early Access check     : http://localhost:8080/access"
Write-Host "    Referral profile       : http://localhost:8080/profile"
Write-Host "    Admin dashboard        : http://localhost:8080/admin"
Write-Host "    Fraud monitor          : http://localhost:8080/admin/fraud"
Write-Host ""
Write-Host "  APIs:"
Write-Host "    Ingestion service      : http://localhost:8081"
Write-Host "    Admin service          : http://localhost:8082"
Write-Host "    Notification service   : http://localhost:8083"
Write-Host ""
Write-Host "  Dev tools:"
Write-Host "    Mailpit (email UI)     : http://localhost:8025"
