param(
    [string]$Suite = "Health",
    [string]$BaseUrl = "http://localhost:8080/v1"
)

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "   FarmFlow API - Empirical k6 Load Testing Suite" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl"
Write-Host "Selected Suite:  $Suite"
Write-Host ""

$env:BASE_URL = $BaseUrl

if ($Suite -eq "Health" -or $Suite -eq "All") {
    Write-Host "[1/2] Executing Health In-Memory Benchmark..." -ForegroundColor Yellow
    if (Get-Command k6 -ErrorAction SilentlyContinue) {
        k6 run load-tests/health-benchmark.js
    } else {
        Write-Host "k6 is not installed on PATH. Run via Docker: docker run --rm -i grafana/k6 run - < load-tests/health-benchmark.js" -ForegroundColor Yellow
    }
}

if ($Suite -eq "Telemetry" -or $Suite -eq "All") {
    Write-Host "[2/2] Executing Transactional Ingestion Benchmark..." -ForegroundColor Yellow
    if (Get-Command k6 -ErrorAction SilentlyContinue) {
        k6 run load-tests/telemetry-ingestion-benchmark.js
    } else {
        Write-Host "k6 is not installed on PATH. Run via Docker: docker run --rm -i grafana/k6 run - < load-tests/telemetry-ingestion-benchmark.js" -ForegroundColor Yellow
    }
}