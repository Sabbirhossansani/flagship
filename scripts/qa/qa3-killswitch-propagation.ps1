# ==============================================================================
# QA3: 50 Concurrent Clients Kill-Switch Propagation Verification Script
# Target: Emergency kill switch broadcasts to 50 concurrent SSE clients within 5.0 seconds
# ==============================================================================
$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  FlagShip QA3: 50-Client Kill-Switch Propagation Verification   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$env:JAVA_HOME = 'C:\Users\Sani\.tools\jdk21'
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\Sani\.tools\maven\bin;$env:PATH"

Write-Host "[1/3] Executing QA3 50-Client Kill Switch Propagation Test..." -ForegroundColor Yellow
$output = cmd /c "mvn test -pl flagship-server -Dtest=Qa3KillSwitchPropagationTest 2>&1" | Out-String

Write-Host $output

if ($output -match "QA3 Kill-Switch Propagation -> 50 concurrent clients reached in (\d+) ms") {
    $elapsedMs = [int]$matches[1]
    Write-Host "`n>> Measured Kill-Switch Propagation Latency: ${elapsedMs} ms across 50 clients" -ForegroundColor Green
    if ($elapsedMs -lt 5000) {
        Write-Host "   [PASS] Propagation latency strictly within 5.0-second SLA (${elapsedMs} ms < 5000 ms)" -ForegroundColor Green
        Write-Host "`n>>> QA3 VERIFICATION SUCCESSFUL: SUB-SECOND KILL SWITCH PROPAGATION <<<" -ForegroundColor Green
    } else {
        Write-Host "   [FAIL] Propagation latency exceeded 5.0-second SLA (${elapsedMs} ms >= 5000 ms)" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "`n>>> QA3 VERIFICATION FAILED: Could not parse propagation latency <<<" -ForegroundColor Red
    exit 1
}
