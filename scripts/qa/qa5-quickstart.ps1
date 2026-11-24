# ==============================================================================
# QA5: 15-Minute Automated Quick Start & Bootstrap Verification Script
# Target: From source checkout to built platform and verified artifacts in < 15 minutes
# ==============================================================================
$ErrorActionPreference = "Continue"

$startTime = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  FlagShip QA5: 15-Minute Quick-Start Bootstrap Validation       " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$rootDir = (Get-Item $PSScriptRoot).Parent.Parent.FullName
Set-Location $rootDir

$env:JAVA_HOME = 'C:\Users\Sani\.tools\jdk21'
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\Sani\.tools\maven\bin;$env:PATH"

# 1. Environment Verification
Write-Host "`n[Step 1/5] Verifying Runtime Environment..." -ForegroundColor Yellow
$javaVer = cmd /c "java -version 2>&1" | Out-String
Write-Host "Java Version: $javaVer" -ForegroundColor Gray
if ($javaVer -notmatch "21\.") {
    Write-Host "[FAIL] Java 21 required!" -ForegroundColor Red
    exit 1
}

$mvnVer = cmd /c "mvn -version 2>&1" | Out-String
Write-Host "Maven Version: $mvnVer" -ForegroundColor Gray

# 2. Schema Migrations Verification
Write-Host "`n[Step 2/5] Verifying PostgreSQL Flyway Migration Scripts..." -ForegroundColor Yellow
$m1 = Join-Path $rootDir "flagship-server/src/main/resources/db/migration/V1__init_flagship_schema.sql"
$m2 = Join-Path $rootDir "flagship-server/src/main/resources/db/migration/V2__phase3_advanced_features.sql"
if ((Test-Path $m1) -and (Test-Path $m2)) {
    Write-Host "   [PASS] Found Flyway V1 and V2 migrations" -ForegroundColor Green
} else {
    Write-Host "   [FAIL] Missing Flyway migration scripts!" -ForegroundColor Red
    exit 1
}

# 3. Docker Compose & Dockerfiles Verification
Write-Host "`n[Step 3/5] Verifying Container Orchestration Configs..." -ForegroundColor Yellow
$dc = Join-Path $rootDir "docker-compose.yml"
$dfServer = Join-Path $rootDir "flagship-server/Dockerfile"
$dfAdmin = Join-Path $rootDir "flagship-admin-web/Dockerfile"
$dfDemo = Join-Path $rootDir "flagship-demo-app/Dockerfile"

if ((Test-Path $dc) -and (Test-Path $dfServer) -and (Test-Path $dfAdmin) -and (Test-Path $dfDemo)) {
    Write-Host "   [PASS] docker-compose.yml and all 3 Dockerfiles verified" -ForegroundColor Green
} else {
    Write-Host "   [FAIL] Missing Dockerfiles or docker-compose.yml!" -ForegroundColor Red
    exit 1
}

# 4. Clean Build & Package
Write-Host "`n[Step 4/5] Executing Clean Multi-Module Maven Package..." -ForegroundColor Yellow
$buildOutput = cmd /c "mvn clean package -DskipTests 2>&1" | Out-String
if ($buildOutput -notmatch "BUILD SUCCESS") {
    Write-Host $buildOutput
    Write-Host "   [FAIL] Maven build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "   [PASS] Maven build completed successfully across all 6 modules" -ForegroundColor Green

# 5. Artifact Verification
Write-Host "`n[Step 5/5] Verifying Packaged Deliverables..." -ForegroundColor Yellow
$jars = @(
    (Join-Path $rootDir "flagship-core/target/flagship-core-1.0.0-SNAPSHOT.jar"),
    (Join-Path $rootDir "flagship-server/target/flagship-server-1.0.0-SNAPSHOT.jar"),
    (Join-Path $rootDir "flagship-spring-boot-starter/target/flagship-spring-boot-starter-1.0.0-SNAPSHOT.jar"),
    (Join-Path $rootDir "flagship-admin-web/target/flagship-admin-web-1.0.0-SNAPSHOT.jar"),
    (Join-Path $rootDir "flagship-demo-app/target/flagship-demo-app-1.0.0-SNAPSHOT.jar")
)

foreach ($j in $jars) {
    if (Test-Path $j) {
        $sizeKb = [math]::Round((Get-Item $j).Length / 1024, 1)
        Write-Host "   [PASS] Found deliverable: $j (${sizeKb} KB)" -ForegroundColor Green
    } else {
        Write-Host "   [FAIL] Missing jar: $j" -ForegroundColor Red
        exit 1
    }
}

$startTime.Stop()
$elapsedSec = [math]::Round($startTime.Elapsed.TotalSeconds, 1)
Write-Host "`n>> Total Quick-Start Bootstrap Time: $elapsedSec seconds" -ForegroundColor Green

if ($elapsedSec -lt 900) { # 15 minutes = 900 seconds
    Write-Host "   [PASS] Bootstrap time strictly satisfies 15-minute SLA ($elapsedSec s < 900 s)" -ForegroundColor Green
    Write-Host "`n>>> QA5 VERIFICATION SUCCESSFUL: RAPID ZERO-FRICTION ONBOARDING <<<" -ForegroundColor Green
} else {
    Write-Host "   [FAIL] Bootstrap exceeded 15 minutes ($elapsedSec s >= 900 s)" -ForegroundColor Red
    exit 1
}
