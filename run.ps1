#Requires -Version 5.1
# Bring up the full waitlist platform and wait until all three Spring services
# report healthy via /actuator/health.
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

Write-Host ""
Write-Host "All services are up."
Write-Host "  Ingestion : http://localhost:8081"
Write-Host "  Admin     : http://localhost:8082"
Write-Host "  Notify    : http://localhost:8083"
Write-Host "  Mailpit   : http://localhost:8025"
