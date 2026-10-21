param (
    [string]$BaseUrl = "http://localhost:8080/v1",
    [string]$KeycloakUrl = "http://localhost:8081",
    [string]$AuthToken = "",
    [string]$K6Path = "k6",
    [ValidateSet("Health", "Domain", "Telemetry", "Stress", "RateLimit", "All")]
    [string]$Suite = "All"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  FarmFlow API — Empirical Performance Benchmark Runner" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl"
Write-Host "Keycloak URL:    $KeycloakUrl"
Write-Host "K6 Executable:   $K6Path"
Write-Host "Selected Suite:  $Suite"

if (-not (Test-Path $K6Path)) {
    $foundK6 = Get-Command k6 -ErrorAction SilentlyContinue
    if ($foundK6) {
        $K6Path = $foundK6.Source
    } else {
        Write-Host "k6 binary not found at '$K6Path' or in PATH. Attempting dockerized k6 run..." -ForegroundColor Yellow
        $K6Path = "docker"
    }
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envArgs = @("--env", "API_BASE_URL=$BaseUrl", "--env", "KEYCLOAK_URL=$KeycloakUrl")
if (-not [string]::IsNullOrWhiteSpace($AuthToken)) {
    $envArgs += @("--env", "AUTH_TOKEN=$AuthToken")
}

function Invoke-K6Script([string]$scriptPath) {
    if ($K6Path -eq "docker") {
        docker run --rm -i -e API_BASE_URL=$BaseUrl --network host grafana/k6 run - < $scriptPath
    } else {
        & $K6Path run $envArgs $scriptPath
    }
}

if ($Suite -eq "Health" -or $Suite -eq "All") {
    Write-Host "`n--- [SUITE: HEALTH / IN-MEMORY BASELINE] ---" -ForegroundColor Magenta
    Write-Host "`n[1/3] Executing Health Baseline Load Test (5 VUs, 30s)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-baseline.js"

    Write-Host "`n[2/3] Executing Health Sustained Load Test (15 VUs, 30s)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-sustained.js"

    Write-Host "`n[3/3] Executing Health Spike Load Test (Ramp to 40 VUs)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-spike.js"
}

if ($Suite -eq "RateLimit" -or $Suite -eq "All") {
    Write-Host "`n--- [SUITE: IN-PROCESS RATE LIMITING BURST] ---" -ForegroundColor Magenta
    Write-Host "`nExecuting Rate Limiting Burst Test (10 VUs, 10s)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-ratelimit-burst.js"
}

if ($Suite -eq "Domain" -or $Suite -eq "Telemetry" -or $Suite -eq "All") {
    Write-Host "`n--- [SUITE: DOMAIN CAPACITY BENCHMARKS] ---" -ForegroundColor Magenta
    Write-Host "`n[1/3] Executing Domain Baseline Load Test (5 VUs, 30s)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-domain-baseline.js"

    Write-Host "`n[2/3] Executing Domain Sustained Load Test (15 VUs, 45s)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-domain-sustained.js"

    Write-Host "`n[3/3] Executing Domain Spike Load Test (5 -> 35 -> 5 VUs)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-domain-spike.js"
}

if ($Suite -eq "Stress" -or $Suite -eq "All") {
    Write-Host "`n--- [SUITE: DOMAIN STRESS & SATURATION BENCHMARK] ---" -ForegroundColor Magenta
    Write-Host "`nExecuting Progressive Stress Test (5 -> 15 -> 30 -> 50 -> 70 VUs)..." -ForegroundColor Yellow
    Invoke-K6Script "$ScriptDir\k6-domain-stress.js"
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "  All selected FarmFlow benchmark scenarios completed." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green