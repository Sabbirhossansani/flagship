# ==============================================================================
# Master QA Verification Runner: Runs QA1 through QA5 and generates scorecard
# ==============================================================================
$ErrorActionPreference = "Continue"

$overallStart = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "=================================================================" -ForegroundColor Magenta
Write-Host "         FlagShip Platform Automated QA Verification Suite        " -ForegroundColor Magenta
Write-Host "=================================================================" -ForegroundColor Magenta

$results = [ordered]@{}

# QA1
Write-Host "`n>>> RUNNING QA1: LATENCY & HEAP BENCHMARK <<<" -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File "$PSScriptRoot/qa1-benchmark.ps1"
$results["QA1 (Latency & Heap Benchmark)"] = ($LASTEXITCODE -eq 0)

# QA2
Write-Host "`n>>> RUNNING QA2: 30-MINUTE OUTAGE SIMULATION <<<" -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File "$PSScriptRoot/qa2-outage-simulation.ps1"
$results["QA2 (30-Min Outage Simulation)"] = ($LASTEXITCODE -eq 0)

# QA3
Write-Host "`n>>> RUNNING QA3: 50-CLIENT KILL-SWITCH PROPAGATION <<<" -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File "$PSScriptRoot/qa3-killswitch-propagation.ps1"
$results["QA3 (50-Client Kill-Switch Propagation)"] = ($LASTEXITCODE -eq 0)

# QA4
Write-Host "`n>>> RUNNING QA4: 1M USER KEY MONOTONICITY & DETERMINISM <<<" -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File "$PSScriptRoot/qa4-distribution.ps1"
$results["QA4 (1M User Monotonicity & Determinism)"] = ($LASTEXITCODE -eq 0)

# QA5
Write-Host "`n>>> RUNNING QA5: 15-MINUTE QUICK-START BOOTSTRAP <<<" -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File "$PSScriptRoot/qa5-quickstart.ps1"
$results["QA5 (15-Min Quick-Start Bootstrap)"] = ($LASTEXITCODE -eq 0)

$overallStart.Stop()
$totalDuration = [math]::Round($overallStart.Elapsed.TotalSeconds, 1)

Write-Host "`n=================================================================" -ForegroundColor Magenta
Write-Host "                   QA VERIFICATION SCORECARD                     " -ForegroundColor Magenta
Write-Host "=================================================================" -ForegroundColor Magenta

$allPassed = $true
foreach ($key in $results.Keys) {
    $status = $results[$key]
    if ($status) {
        Write-Host "  [PASS] $key" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $key" -ForegroundColor Red
        $allPassed = $false
    }
}

Write-Host "`nTotal Test & Benchmark Duration: $totalDuration seconds" -ForegroundColor Gray
if ($allPassed) {
    Write-Host "ALL QUALITY ATTRIBUTES (QA1-QA5) EMPIRICALLY SATISFIED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SOME QUALITY ATTRIBUTES FAILED! Check logs above." -ForegroundColor Red
    exit 1
}
