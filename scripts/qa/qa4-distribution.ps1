# ==============================================================================
# QA4: 1,000,000 User Key Determinism & Monotonicity Verification Script
# Target: MurmurHash3 determinism (0 mismatches), statistical uniformity, and monotonic gradual rollout
# ==============================================================================
$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " FlagShip QA4: 1M User Key Determinism & Monotonicity Benchmark  " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$env:JAVA_HOME = 'C:\Users\Sani\.tools\jdk21'
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\Sani\.tools\maven\bin;$env:PATH"

Write-Host "[1/3] Executing QA4 Determinism and Monotonicity Test on 1,000,000 Keys..." -ForegroundColor Yellow
$output = cmd /c "mvn test -pl flagship-core -Dtest=Qa4DeterminismAndMonotonicityTest 2>&1" | Out-String

Write-Host $output

if ($output -match "QA4 1,000,000 Users -> 10% Cohort: (\d+) \| 20% Cohort: (\d+) \| Mismatches: (\d+) \| Monotonicity Violations: (\d+)") {
    $c10 = [int]$matches[1]
    $c20 = [int]$matches[2]
    $mismatches = [int]$matches[3]
    $violations = [int]$matches[4]

    Write-Host "`n>> 1,000,000 Evaluated Users:" -ForegroundColor Green
    Write-Host "   - 10% Target Cohort Size: $c10 (Expect ~100,000 | Measured: $([math]::Round($c10/10000, 2))%)" -ForegroundColor Green
    Write-Host "   - 20% Target Cohort Size: $c20 (Expect ~200,000 | Measured: $([math]::Round($c20/10000, 2))%)" -ForegroundColor Green
    Write-Host "   - Determinism Mismatches: $mismatches (MUST BE 0)" -ForegroundColor Green
    Write-Host "   - Monotonicity Violations: $violations (MUST BE 0)" -ForegroundColor Green

    if ($mismatches -eq 0 -and $violations -eq 0) {
        Write-Host "`n>>> QA4 VERIFICATION SUCCESSFUL: 100% DETERMINISTIC & MONOTONIC <<<" -ForegroundColor Green
    } else {
        Write-Host "`n>>> QA4 VERIFICATION FAILED: Violations detected <<<" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "`n>>> QA4 VERIFICATION FAILED: Could not parse distribution metrics <<<" -ForegroundColor Red
    exit 1
}
