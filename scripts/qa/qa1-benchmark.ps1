# ==============================================================================
# QA1: Local Evaluation Latency & Heap Footprint Verification Script
# Target: p99 Latency <= 50 microseconds for 10-rule evaluation | Heap <= 20 MB per 1,000 flags
# ==============================================================================
$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  FlagShip QA1: Latency & Heap Footprint Benchmark Verification  " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$env:JAVA_HOME = 'C:\Users\Sani\.tools\jdk21'
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\Sani\.tools\maven\bin;$env:PATH"

Write-Host "[1/3] Executing QA1 Performance Benchmark via Maven..." -ForegroundColor Yellow
$output = cmd /c "mvn test -pl flagship-core -Dtest=Qa1PerformanceBenchmarkTest 2>&1" | Out-String

Write-Host $output

if ($output -match "QA1 1,000 Flags Heap Overhead -> ([\d\.]+) MB") {
    $heapMb = [double]$matches[1]
    Write-Host "`n>> Measured Heap Overhead: $heapMb MB per 1,000 flags" -ForegroundColor Green
    if ($heapMb -le 20.0) {
        Write-Host "   [PASS] Heap constraint satisfied (<= 20.0 MB)" -ForegroundColor Green
    } else {
        Write-Host "   [FAIL] Heap constraint violated (> 20.0 MB)" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[WARN] Could not parse Heap Overhead metric" -ForegroundColor Yellow
    exit 1
}

if ($output -match "QA1 10-Rule Evaluation Latency -> p50: ([\d\.]+) us \| p95: ([\d\.]+) us \| p99: ([\d\.]+) us") {
    $p50 = [double]$matches[1]
    $p95 = [double]$matches[2]
    $p99 = [double]$matches[3]
    Write-Host ">> Measured Latency: p50: ${p50}us | p95: ${p95}us | p99: ${p99}us" -ForegroundColor Green
    if ($p99 -le 50.0) {
        Write-Host "   [PASS] p99 Latency constraint satisfied (<= 50.0 us)" -ForegroundColor Green
    } else {
        Write-Host "   [FAIL] p99 Latency constraint violated (> 50.0 us)" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[WARN] Could not parse Latency metric" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n>>> QA1 VERIFICATION SUCCESSFUL: SLA STRICTLY SATISFIED <<<" -ForegroundColor Green
