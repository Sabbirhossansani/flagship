# FlagShip Quality Attribute Verification Evidence Report (C9)

## 1. Executive Summary

This report presents empirical benchmark measurements and automated test results validating the five primary Quality Attributes (QA1 through QA5) of the FlagShip Platform.

All verification benchmarks were executed on a standard developer workstation under Windows 11 with OpenJDK 21 (build 21.0.5+11-LTS) using the master automated test runner `scripts/qa/run-all-qa.ps1`.

### Summary Scorecard

| Quality Attribute | Requirement Description | Threshold / SLA | Measured Result | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **QA1 (Speed & Memory)** | 10-rule flag local evaluation latency & 1,000 flags heap retention | p99 $\le$ 50.0 µs<br>Heap $\le$ 20.0 MB | **p99: 7.10 µs** (p50: 1.20 µs)<br>**Heap: 5.38 MB** | **PASS** |
| **QA2 (Outage Resilience)**| 30-minute complete server outage; zero exceptions; cold recovery | 100% Availability<br>Zero Exceptions | **10,000 / 10,000 served**<br>**0 Exceptions** | **PASS** |
| **QA3 (Propagation)** | Kill-switch broadcast reaching 50 concurrent SSE client instances | Latency < 5.0 s at p95 | **5 milliseconds** | **PASS** |
| **QA4 (Consistency)** | 1,000,000 user keys: client/server preview match & monotonic rollout | 0 Mismatches<br>0 Monotonicity Breaks | **0 Mismatches**<br>**0 Violations** | **PASS** |
| **QA5 (Adoption)** | Source checkout to clean multi-module build, packaging, and verify | Total Time < 15 mins (900 s) | **43.0 seconds** | **PASS** |

---

## 2. Deep Quality Attribute Analysis & Raw Evidence

### 2.1 QA1: Local Evaluation Latency & Memory Footprint

#### Raw Benchmark Output
```
QA1 1,000 Flags Heap Overhead -> 5.38 MB (flags loaded: 1000)
QA1 10-Rule Evaluation Latency -> p50: 1.20 us | p95: 3.50 us | p99: 7.10 us
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.791 s
>> Measured Heap Overhead: 5.38 MB per 1,000 flags
   [PASS] Heap constraint satisfied (<= 20.0 MB)
>> Measured Latency: p50: 1.2us | p95: 3.5us | p99: 7.1us
   [PASS] p99 Latency constraint satisfied (<= 50.0 us)
```

#### Latency Distribution Comparison Chart
```
Target SLA (p99 <= 50.0 µs):
[==================================================] 50.00 µs (Limit)

Measured Evaluation Latency:
p50: [=] 1.20 µs
p95: [===] 3.50 µs
p99: [======] 7.10 µs
```
*Analysis*: Even under 10 complex targeting rules combining boolean AND/OR nodes, semantic version comparisons, and regex checks, p99 evaluation latency was **7.10 µs**, which is **85.8% lower than the 50 µs threshold**.

#### Heap Memory Retention Chart
```
Allowed Budget: [========================================] 20.00 MB
Actual Footprint: [==========] 5.38 MB (1,000 active flags)
```

---

### 2.2 QA2: 30-Minute Outage Simulation

#### Raw Test Output
```
[INFO] Running com.flagship.starter.sync.Qa2OutageSimulationTest
07:23:02.196 [main] INFO com.flagship.starter.sync.DiskPersistenceManager -- Loaded cached FlagShip snapshot (rev=10) from disk
QA2 Outage Simulation -> 10,000 requests during 30-min blackout: 100% served from local cache
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.684 s
>> Outage Simulation: 10,000/10,000 requests successfully evaluated from cold disk cache
>> Fallback values returned safely without exceptions for unknown flags
>> Resynchronization after simulated server recovery verified (rev=10 -> rev=11)
```

*Analysis*:
1. Application cold-started with `flagship-server` down.
2. `DiskPersistenceManager` recovered `rev=10` snapshot.
3. 10,000 evaluations were performed across known flags and non-existent fallback flags.
4. Exactly **zero** exceptions occurred.
5. Server recovery simulated with `rev=11` snapshot; the client synchronized smoothly.

---

### 2.3 QA3: 50-Client Kill-Switch Propagation

#### Raw Test Output
```
[INFO] Running com.flagship.server.service.Qa3KillSwitchPropagationTest
QA3 Kill-Switch Propagation -> 50 concurrent clients reached in 5 ms (p99 < 5000ms SLA PASSED)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.158 s
>> Measured Kill-Switch Propagation Latency: 5 ms across 50 clients
   [PASS] Propagation latency strictly within 5.0-second SLA (5 ms < 5000 ms)
```

#### Propagation Timeline
```
Time 0 ms:      Kill-switch triggered on Server
Time 1 ms:      Command committed; SSE broadcaster invoked
Time 2-5 ms:    Virtual-thread fan-out delivers event to all 50 concurrent clients
Time 5 ms:      50/50 clients have updated in-memory flag to disabled (killSwitch=true)
SLA Threshold:  5,000 ms
```

---

### 2.4 QA4: 1,000,000 User Key Determinism & Monotonic Rollout

#### Raw Test Output
```
[INFO] Running com.flagship.core.evaluation.Qa4DeterminismAndMonotonicityTest
QA4 1,000,000 Users -> 10% Cohort: 100112 | 20% Cohort: 200299 | Mismatches: 0 | Monotonicity Violations: 0
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.542 s
>> 1,000,000 Evaluated Users:
   - 10% Target Cohort Size: 100112 (Expect ~100,000 | Measured: 10.01%)
   - 20% Target Cohort Size: 200299 (Expect ~200,000 | Measured: 20.03%)
   - Determinism Mismatches: 0 (MUST BE 0)
   - Monotonicity Violations: 0 (MUST BE 0)
```

#### Cohort Consistency Visual
```
1,000,000 Keys Evaluated:
Initial 10% Cohort: [##########..................................................] 100,112 users
Expanded 20% Cohort:[####################........................................] 200,299 users
Preserved Users:    [##########] 100% of 10% cohort remained in 20% cohort.
Monotonicity Leaks: 0 users dropped
Determinism Delta:  0 variations differed between client engine and server preview
```

---

### 2.5 QA5: 15-Minute Automated Quick Start & Bootstrap

#### Raw Script Output
```
[Step 1/5] Verifying Runtime Environment...
Java Version: openjdk version "21.0.5" 2024-10-15 LTS
Maven Version: Apache Maven 3.9.9

[Step 2/5] Verifying PostgreSQL Flyway Migration Scripts...
   [PASS] Found Flyway V1 and V2 migrations

[Step 3/5] Verifying Container Orchestration Configs...
   [PASS] docker-compose.yml and all 3 Dockerfiles verified

[Step 4/5] Executing Clean Multi-Module Maven Package...
   [PASS] Maven build completed successfully across all 6 modules

[Step 5/5] Verifying Packaged Deliverables...
   [PASS] Found deliverable: flagship-core-1.0.0-SNAPSHOT.jar (70.3 KB)
   [PASS] Found deliverable: flagship-server-1.0.0-SNAPSHOT.jar (67927.9 KB)
   [PASS] Found deliverable: flagship-spring-boot-starter-1.0.0-SNAPSHOT.jar (35.9 KB)
   [PASS] Found deliverable: flagship-admin-web-1.0.0-SNAPSHOT.jar (19497.9 KB)
   [PASS] Found deliverable: flagship-demo-app-1.0.0-SNAPSHOT.jar (26249.3 KB)

>> Total Quick-Start Bootstrap Time: 43 seconds
   [PASS] Bootstrap time strictly satisfies 15-minute SLA (43 s < 900 s)
```

---

## 3. Failed-Run Log and Engineering Root-Cause Fixes

In accordance with **Constraint C9**, all failed verification runs encountered during development are documented below along with the exact root-cause diagnosis and code fixes applied:

### Issue 1: PowerShell NativeCommandError Aborting Automated Runner
- **Symptom**: `scripts/qa/run-all-qa.ps1` failed on QA1 and QA4 with:
  `CategoryInfo : NotSpecified: (SLF4J(W): No SLF4J providers were found.:String) [], RemoteException`
  `FullyQualifiedErrorId : NativeCommandError`
- **Root Cause**:
  1. The PowerShell scripts configured `$ErrorActionPreference = "Stop"`.
  2. Maven and `java -version` emit diagnostic logs (such as SLF4J missing-provider notices or VM version strings) to `stderr`.
  3. In PowerShell, combining `$ErrorActionPreference = "Stop"` with `2>&1` causes any native process writing to `stderr` to be treated as a terminating `NativeCommandError`, aborting script execution prematurely even when the underlying exit code was `0` (Success).
- **Fix**:
  1. Updated the runner scripts to execute commands via `cmd /c "mvn ... 2>&1"` and `cmd /c "java -version 2>&1"`.
  2. Changed error action preference to `$ErrorActionPreference = "Continue"`.
  3. Ensured that pass/fail criteria check the process exit code (`$LASTEXITCODE -eq 0`) and standard text assertions rather than triggering on harmless stderr diagnostics.

### Issue 2: QA5 Flyway Migration Script Path Discrepancy
- **Symptom**: QA5 failed at Step 2 with `[FAIL] Missing Flyway migration scripts!`.
- **Root Cause**: The verification script looked for `V1__initial_schema.sql`, whereas the actual migration file in `flagship-server/src/main/resources/db/migration/` was named `V1__init_flagship_schema.sql`.
- **Fix**: Corrected the file path check in `qa5-quickstart.ps1` to reference `V1__init_flagship_schema.sql` and used `Join-Path $rootDir ...` to make paths robust to execution from any working directory.
- **Verification**: Re-ran `qa5-quickstart.ps1`; step 2 passed immediately.

### Issue 3: Hardcoded 10.0 µs Threshold in QA1 Wrapper Script
- **Symptom**: `qa1-benchmark.ps1` had hardcoded `if ($p99 -lt 10.0)`, while the project specification stated: "Local evaluation of a flag with 10 rules takes at most 50 µs at p99".
- **Root Cause**: Discrepancy between author's exploratory threshold (10.0 µs) and the official project specification (50.0 µs).
- **Fix**: Aligned `qa1-benchmark.ps1` with the specification (`if ($p99 -le 50.0)`). The measured p99 of **7.10 µs** comfortably satisfies both.

### Issue 4: GitHub Actions CI Failure — Cross-Test Database State Leakage (`FlagshipServerIntegrationTest.java:89`)
- **Symptom**: GitHub Actions CI Run #8 on commit `dec6f1f` (`ubuntu-latest`) failed `FlagshipServerIntegrationTest.fullControlPlaneLifecycleWithJwtRbacEvaluationPreviewAndSnapshot` at line 89 with `JSON path "$.totalElements" expected:<1> but was:<2>`.
- **Root Cause**: `application-test.yml` uses `jdbc:h2:mem:flagship_test;...;DB_CLOSE_DELAY=-1`, which persists database state across test classes in the same JVM. `Phase3AdvancedFeaturesIntegrationTest` inserted project `p3-proj` in `@BeforeEach` and committed it during the non-transactional `sseStreamSubscription()` test without cleaning up afterwards. Windows NTFS happened to execute `FlagshipServerIntegrationTest` first, whereas Linux `ext4` in CI executed `Phase3AdvancedFeaturesIntegrationTest` first and leaked `p3-proj` into the `projects` table.
- **Fix**:
  1. Added `TestDatabaseCleaner.cleanAllTables(JdbcTemplate)` to delete rows across all 10 Flyway V1/V2 tables (`experiment_events`, `experiments`, `scheduled_flag_changes`, `change_requests`, `flag_change_history`, `project_role_assignments`, `flags`, `segments`, `environments`, `projects`) in foreign-key order.
  2. Applied `@BeforeEach` / `@AfterEach` database resets across `FlagshipServerIntegrationTest` (and `PostgresTestcontainersIntegrationTest`), `Phase3AdvancedFeaturesIntegrationTest`, and `@BeforeEach` on `FlagshipRepositoryDataJpaSliceTest`.
  3. Removed redundant environment creation and test-level `@Transactional` in `Phase3AdvancedFeaturesIntegrationTest`, and closed the test `SseEmitter` cleanly.
- **Verification**: Verified locally under both forced reverse alphabetical test execution (`mvn test -pl flagship-server "-Dsurefire.runOrder=reversealphabetical"`) and full `mvn clean test` with 0 failures and 0 errors.
