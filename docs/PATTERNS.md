# FlagShip Design Pattern Catalog & Justification Table

## 1. Executive Summary

Architectural design patterns in FlagShip are directly tied to specific functional requirements (FR1–FR10) and measurable quality attributes (QA1–QA5). Patterns are never used as speculative ceremony; each pattern solves an explicit architectural tension between flexibility, latency, memory footprint, and fault isolation.

---

## 2. Pattern Justification Table (Constraint C8)

| Design Pattern | Requirement Served | Participating Classes | Alternative Rejected & Rationale |
| :--- | :--- | :--- | :--- |
| **Composite** | **FR2**: Complex nested targeting rules combining conditions with arbitrary `AND` / `OR` logical operators. | `RuleNode`<br>`LeafRuleNode`<br>`CompositeRuleNode` | **Rejected: Flat list of boolean clauses.**<br>*Rationale*: A flat list cannot model nested Boolean logic such as `(tier == 'gold' AND (region == 'NA' OR beta_tester == true))`. Composite enables recursive tree structures evaluated uniformly. |
| **Visitor** | **FR2**, **QA1**: Rule tree evaluation, serialization, and rule static validation without polluting rule nodes with evaluation state. | `RuleVisitor`<br>`EvaluationVisitor`<br>`RuleNode.accept(visitor)` | **Rejected: Concrete evaluation methods inside node classes (`node.evaluate(ctx)`).**<br>*Rationale*: Violates Single Responsibility and Open-Closed Principles. Adding new operations (e.g., AST visualization, SQL export, syntax validation) would require modifying all node classes. Visitor decouples tree structure from operations. |
| **Chain of Responsibility** | **FR2**: Strict precedence ordering: Kill Switch -> Individual Targets -> Segment Rules -> Rollout % -> Default Variation. | `EvaluationHandler`<br>`KillSwitchHandler`<br>`UserTargetingHandler`<br>`SegmentRuleHandler`<br>`PercentageRolloutHandler`<br>`FallbackHandler`<br>`FlagEvaluationEngine` | **Rejected: Monolithic `if-else` block in evaluation service.**<br>*Rationale*: Monolithic evaluation creates tight coupling, makes unit testing of individual evaluation rules fragile, and prevents dynamic reordering or insertion of custom evaluation stages (e.g., future geofencing or compliance filters). |
| **Strategy** | **FR2**, **FR3**: Attribute comparisons (`EQUALS`, `IN`, `CONTAINS`, `SEMVER_GT`, `REGEX`, `DATE_AFTER`, etc.) and uniform user bucketing algorithms. | `RuleOperator`<br>`EqualsOperator`<br>`InOperator`<br>`ContainsOperator`<br>`SemVerGreaterThanOperator`<br>`RegexOperator`<br>`DateAfterOperator`<br>`BucketingStrategy`<br>`MurmurHash3Bucketer` | **Rejected: Giant `switch(operator)` statement with inline regex compiling.**<br>*Rationale*: Giant switches require recompiling regexes on every check or maintaining global mutable caches. The Strategy pattern encapsulates each comparison operator, allowing precompiled patterns, clean polymorphism, and O(1) operator lookups. |
| **Observer** | **FR5**, **QA3**: Real-time push updates to connected client applications via Server-Sent Events (SSE). | `SseBroadcasterService`<br>`SseEmitter`<br>`FlagChangeListener`<br>`DefaultFlagShipClient` | **Rejected: Polling-only architecture.**<br>*Rationale*: Polling 50+ microservices every 1 second creates massive DB load and cannot guarantee sub-5-second kill-switch propagation (QA3). Observer via SSE provides push notifications within 5 ms while falling back to polling if the connection drops. |
| **Proxy / Decorator** | **FR6**, **QA2**: Resilient, thread-safe client wrapping in-memory snapshots, local disk persistence, and safe default fallback. | `FlagShipClient`<br>`DefaultFlagShipClient`<br>`DiskPersistenceManager` | **Rejected: Inlining disk I/O and caching directly inside business controllers.**<br>*Rationale*: The Proxy/Decorator decouples application code from caching, persistence, and synchronization mechanics. Application code interacts with a simple interface (`client.isEnabled(...)`), while the underlying decorator handles thread safety and cold-cache recovery. |
| **Command** | **FR7**: Audit trail, versioned mutations, two-person approval queues, and one-click rollback. | `FlagCommand`<br>`CreateFlagCommand`<br>`UpdateFlagCommand`<br>`RollbackFlagCommand`<br>`FlagCommandService`<br>`AuditLog` | **Rejected: Direct in-place SQL updates (`UPDATE flags SET ...`).**<br>*Rationale*: Direct database updates lose intermediate states, require custom rollback scripts, and cannot support two-person approval workflows. Encapsulating changes as Command objects enables serialization, diff generation, undo execution, and deferred scheduling. |
| **Builder** | **FR1**, **FR2**: Fluent construction of complex immutable models (`FlagDefinition`, `EvaluationContext`, `TargetingRule`). | `FlagDefinition.Builder`<br>`EvaluationContext.Builder`<br>`TargetingRule.Builder` | **Rejected: Telescoping constructors or mutable JavaBeans.**<br>*Rationale*: Telescoping constructors are error-prone with 10+ optional parameters. Mutable JavaBeans permit race conditions across threads during concurrent evaluations. Builders enforce immutability while maintaining readable construction. |

---

## 3. Architectural Position on the Singleton Pattern

The **Singleton Pattern** is often misused in enterprise Java, acting as a masked global variable that impairs testability and concurrency. In FlagShip, we take a **reasoned, explicit architectural position**:

### 3.1 What is NOT a Singleton: Domain State
- `EnvironmentSnapshot`, `FlagDefinition`, `EvaluationContext`, and `TargetingRule` are **strictly immutable records/classes**.
- They are **NEVER** singletons. New instances are created upon mutation and swapped atomically via `AtomicReference<EnvironmentSnapshot>`.
- This ensures zero lock contention, wait-free reads, and total isolation between concurrent thread evaluations.

### 3.2 Where Singleton IS Used: Managed Infrastructure Component
- In the Spring Boot container, `DefaultFlagShipClient` and `FlagEvaluationEngine` are registered as **Spring-managed Singletons** (`@Component` / `@Service`).
- **Why**: An application needs exactly one background SSE connection, one scheduled polling executor, and one in-memory snapshot cache per JVM. Creating multiple client instances would duplicate SSE socket connections and waste memory.
- **Testability**: The engine and client take their dependencies via constructor injection, allowing unit and slice tests to instantiate fresh, unshared instances on demand without Spring context overhead.
