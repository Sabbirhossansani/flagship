# FlagShip AI Usage & Process Evidence Log (Constraint C11)

## 1. Overview & Tooling Environment

In accordance with **Constraint C11**, this log documents the interactive human-AI engineering workflow utilized to design, implement, benchmark, and verify the FlagShip platform.

- **Development Platform**: Windows 11 Enterprise (x64)
- **Runtime & Compilers**: OpenJDK 21.0.5 LTS (Microsoft build), Apache Maven 3.9.9
- **Container Infrastructure**: Docker Engine v27+, Docker Compose v2.29+
- **AI Tools Used**: Google DeepMind Antigravity Agent, Claude 3.5 Sonnet / Gemini 2.0 Pro architecture reasoning assistants.

---

## 2. Phase-by-Phase Incremental Delivery Plan

The project was structured into strictly decoupled phases with independent Git commits to `origin main`:

| Phase | Git Commit Hash | Scope & Deliverables | Test Status |
| :--- | :--- | :--- | :--- |
| **Phase 1** | `d4d85a5` | Multi-module Maven POM, `flagship-core` domain engine, MurmurHash3 bucketer, Composite/Visitor rule tree, Chain of Responsibility evaluator, JMH benchmark, CI workflow. | 16 Tests Passed |
| **Phase 2** | `e11f114` | `flagship-server` Spring Boot 4.x application, PostgreSQL Flyway migrations (V1), JPA entities, REST controllers, Spring Security RBAC, OpenAPI springdoc, Problem Details error handling. | 34 Tests Passed |
| **Phase 3** | `aad3e2e` | Advanced server capabilities: SSE fan-out broadcaster (`SseBroadcasterService`), Change Control with Command pattern, 2-person approvals, one-action rollback, Experiments with SRM & Z-tests, and Flag Hygiene stale flag detector. | 43 Tests Passed |
| **Phase 4** | `73fdd6a` | `flagship-spring-boot-starter` with `@AutoConfiguration`, `@FeatureGuard` AOP aspect, in-memory cache, local disk persistence, SSE client with polling fallback, `/actuator/flagship` endpoint, and `flagship-demo-app`. | 47 Tests Passed |
| **Phase 5** | `8df5b8a` | `flagship-admin-web` dashboard (Thymeleaf/HTMX/Tailwind), responsive UI for flags/segments/audit/experiments, Dockerfiles for all microservices, and `docker-compose.yml` multi-container orchestration. | 47 Tests Passed |
| **Phase 6** | `1f467de` | ArchUnit architectural fitness tests (C6) enforcing module boundaries and pure Java domain constraints; runnable QA1–QA5 automated scripts in `scripts/qa/`. | 50 Tests Passed |
| **Phase 7** | `8a95084` | C4 architecture diagrams, CAP theorem analysis, 4 ADRs, Mini-ATAM utility tree, Pattern justification table (C8), Evidence report (C9), AI usage log (C11), and comprehensive README.md. | 50 Tests Passed |
| **Hardening** | `dec6f1f` | Fixed two-person ChangeControl approvals (rules/variations/deletions), Observer deletion events, private `@FeatureGuard` fallback reflection, and Docker Compose wiring. | 54 Local Tests Passed (CI Failed on Test Leakage) |
| **CI Test Isolation Fix** | *(Current)* | Added `TestDatabaseCleaner` (`@BeforeEach`/`@AfterEach` cleanup across all 10 Flyway tables) for integration and `@DataJpaTest` suites, fixed `Phase3AdvancedFeaturesIntegrationTest` transaction/SSE leaks, and updated C9/C11 docs. | All Tests Passed |

---

## 3. Detailed Case Studies of AI Imperfections and Engineering Fixes

Constraint C11 requires documenting **at least three specific cases** where the AI agent's initial output was incorrect, incomplete, or flawed, including how the flaw was detected, diagnosed, and resolved:

### Case Study 1: PowerShell NativeCommandError Aborting Automated Runner
- **Initial AI Output**: The AI generated PowerShell automation scripts (`qa1-benchmark.ps1`, `qa4-distribution.ps1`, `qa5-quickstart.ps1`) using:
  ```powershell
  $ErrorActionPreference = "Stop"
  $output = mvn test -pl flagship-core -Dtest=... 2>&1 | Out-String
  ```
- **How It Was Found**: When running `scripts/qa/run-all-qa.ps1`, the runner unexpectedly failed on QA1 and QA4 with a terminating `NativeCommandError`:
  `CategoryInfo : NotSpecified: (SLF4J(W): No SLF4J providers were found.:String) [], RemoteException`
- **Root-Cause Analysis**: In PowerShell 5.1/7+, setting `$ErrorActionPreference = "Stop"` instructs PowerShell to convert any standard error stream (`stderr`) emitted by a native executable into a terminating exception. Maven and Java routinely write informational notices (e.g., SLF4J missing-provider notices or `java -version` metadata) to `stderr`. Capturing `2>&1` caused PowerShell to falsely believe Maven crashed.
- **How It Was Fixed**:
  1. We modified the scripts to set `$ErrorActionPreference = "Continue"`.
  2. We wrapped native invocations through the Windows command shell: `cmd /c "mvn ... 2>&1"`.
  3. We evaluated pass/fail status strictly by checking process return codes (`$LASTEXITCODE -eq 0`) and parsing verified benchmark output lines rather than aborting on stderr messages.

### Case Study 2: Hardcoded Flyway Migration Script Name Mismatch
- **Initial AI Output**: In `qa5-quickstart.ps1`, the AI wrote a verification step checking for the presence of Flyway migration files:
  ```powershell
  $m1 = "flagship-server/src/main/resources/db/migration/V1__initial_schema.sql"
  ```
- **How It Was Found**: QA5 failed during automated execution with `[FAIL] Missing Flyway migration scripts!`.
- **Root-Cause Analysis**: In Phase 2, the Flyway migration was committed with the filename `V1__init_flagship_schema.sql` rather than `V1__initial_schema.sql`. The AI generated the QA5 script from a generic prompt assumption rather than inspecting the actual repository filesystem.
- **How It Was Fixed**:
  1. We inspected `flagship-server/src/main/resources/db/migration/` and confirmed the actual file was `V1__init_flagship_schema.sql`.
  2. We updated `qa5-quickstart.ps1` to reference the true filename and used `Join-Path $rootDir ...` to ensure paths resolve correctly regardless of execution directory.

### Case Study 3: SseBroadcasterService Emitter Injection Limitation
- **Initial AI Output**: In Phase 3, `SseBroadcasterService` instantiated its `SseEmitter` instances internally:
  ```java
  public SseEmitter subscribe(String projectKey, String envKey) {
      String key = buildKey(projectKey, envKey);
      SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
      ...
      return emitter;
  }
  ```
- **How It Was Found**: In Phase 6, when developing `Qa3KillSwitchPropagationTest.java` to empirically measure kill-switch propagation across 50 concurrent clients, it was impossible to inject mock or spy `SseEmitter` instances to record precise millisecond timestamps when individual clients received the broadcast event.
- **Root-Cause Analysis**: Hardcoding `new SseEmitter(...)` inside the service violated dependency inversion and testability principles.
- **How It Was Fixed**:
  1. Overloaded `subscribe(String projectKey, String envKey, SseEmitter emitter)` to accept a caller-provided emitter, delegating `subscribe(projectKey, envKey)` to it.
  2. This allowed `Qa3KillSwitchPropagationTest` to inject custom `SseEmitter` instances overriding `send()`, recording nanosecond timestamps across 50 virtual clients and proving 5 ms propagation.

### Case Study 4: Two-Person Change Request Approval Dropping Rules & Variations
- **Initial AI Output**: In Phase 3, `ChangeControlService.applyChangeRequestPayload` only transferred `name`, `description`, `enabled`, `killSwitch`, and `rolloutPercentage` when approving an `UPDATE` change request.
- **How It Was Found**: Sceptical code review identified that if a user submitted a change request containing targeting rules, new variations, weighted percentage rollouts, user targets, or custom owners in production, approving the change request silently discarded the targeting rules and variations.
- **Root-Cause Analysis**: Incomplete deserialization and mapping in `applyChangeRequestPayload`, along with missing `DELETE` approval support.
- **How It Was Fixed**:
  1. Updated `applyChangeRequestPayload` to parse and apply all `UpdateFlagRequest` fields (`variations`, `rules`, `percentageRollout`, `individualTargets`, `defaultVariationKey`, `offVariationKey`, `owner`).
  2. Added support for `DELETE` change request approvals.
  3. Added comprehensive automated integration tests in `Phase3AdvancedFeaturesIntegrationTest.java` verifying rules, variations, and flag deletion upon approval.

### Case Study 5: Observer Pattern Flag Deletion Notification Omission
- **Initial AI Output**: In Phase 4, `DefaultFlagShipClient.notifyChanges` only iterated over `newSnapshot.flags().entrySet()`, checking for modified or newly added flags.
- **How It Was Found**: In inspecting the `FlagChangeListener` contract, the javadoc explicitly states `@param newDefinition the updated flag definition (or null if deleted)`. However, when a flag was deleted from an environment snapshot, listeners were never called with `null`.
- **Root-Cause Analysis**: The change detection logic did not compare `oldSnapshot.flags().keySet()` against `newSnapshot.flags()`.
- **How It Was Fixed**:
  1. Added a second pass in `DefaultFlagShipClient.notifyChanges` iterating over `oldFlags.keySet()` to find deleted keys and fire `listener.onFlagChanged(oldKey, null)`.
  2. Added unit test `DefaultFlagShipClientUnitTest.changeListener_notifiesUpdatesAndDeletions` verifying that additions, updates, and deletions are all properly emitted.

### Case Study 6: GitHub Actions CI Failure Due to Cross-Test H2 Database State Leakage (`FlagshipServerIntegrationTest.java:89`)
- **Initial AI Output**: `Phase3AdvancedFeaturesIntegrationTest` created project `p3-proj` in `@BeforeEach` (including a redundant `staging` environment creation caught by an empty `try/catch`), left `sseStreamSubscription()` non-transactional so `p3-proj` was permanently committed, and `FlagshipServerIntegrationTest` had no `@BeforeEach`/`@AfterEach` table cleanup.
- **How It Was Found**: GitHub Actions CI Run #8 on commit `dec6f1f` (`ubuntu-latest`) failed `FlagshipServerIntegrationTest.fullControlPlaneLifecycleWithJwtRbacEvaluationPreviewAndSnapshot` at line 89 (`jsonPath("$.totalElements").value(1)` failed because Actual was `2`). On Windows NTFS, Surefire discovered and ran `FlagshipServerIntegrationTest` before `Phase3AdvancedFeaturesIntegrationTest`, masking the leak; on Linux `ext4`, `Phase3AdvancedFeaturesIntegrationTest` ran first and left `p3-proj` in the `projects` table.
- **Root-Cause Analysis**:
  1. `application-test.yml` shares a persistent in-memory H2 instance (`jdbc:h2:mem:flagship_test;...;DB_CLOSE_DELAY=-1`) across Spring test contexts in the same JVM.
  2. `Phase3AdvancedFeaturesIntegrationTest.sseStreamSubscription()` was not `@Transactional`, so `p3-proj` committed in `@BeforeEach` remained in `projects`.
  3. Flyway `V2__phase3_advanced_features.sql` added 5 audit/experiment/scheduling tables without foreign-key cascades from `projects`, requiring explicit child-before-parent cleanup across all 10 tables.
- **How It Was Fixed**:
  1. Created `TestDatabaseCleaner.cleanAllTables(JdbcTemplate)` in `flagship-server` test sources to clear all 10 Flyway V1/V2 tables in strict child-before-parent FK order (compatible with both H2 and PostgreSQL 16).
  2. Wired `@BeforeEach` and `@AfterEach` cleanup into `FlagshipServerIntegrationTest` (also inherited by `PostgresTestcontainersIntegrationTest`), `Phase3AdvancedFeaturesIntegrationTest`, and `@BeforeEach` into `FlagshipRepositoryDataJpaSliceTest`.
  3. Removed redundant `staging` creation and test-level `@Transactional` in `Phase3AdvancedFeaturesIntegrationTest`, and ensured `sseStreamSubscription()` closes its `SseEmitter` via `finally { emitter.complete(); }`.
  4. Verified with forced reverse execution (`-Dsurefire.runOrder=reversealphabetical`) and full `mvn clean test`.
