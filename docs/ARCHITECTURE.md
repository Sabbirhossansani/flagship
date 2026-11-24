# FlagShip Architectural Blueprint & C4 Specification

## 1. Executive Summary

**FlagShip** is a high-throughput, enterprise-grade feature flagging and gradual-rollout platform designed for mission-critical microservice ecosystems. It pairs a centralized management control plane (`flagship-server` and `flagship-admin-web`) with an ultra-low-latency, zero-dependency domain evaluation engine (`flagship-core`) and an idiomatic `flagship-spring-boot-starter`.

The guiding architectural principle of FlagShip is **Decoupled Local Evaluation**: while flag definitions, segmentation rules, change control, and experimentation analytics are managed centrally, all runtime evaluation decisions are computed **in-process** within the consuming application using locally cached, immutable snapshots.

---

## 2. CAP Theorem Analysis & Architectural Trade-offs

Under Eric Brewer's CAP Theorem, a distributed data system can simultaneously guarantee at most two out of three properties: **Consistency (C)**, **Availability (A)**, and **Partition Tolerance (P)**.

In production feature management, network partitions between applications and the flag management control plane are inevitable due to network splits, server redeployments, configuration errors, or upstream database outages.

```
                     Consistency (C)
                         /     \
                        /   *   \
                       / (RDBMS) \
                      /           \
    Partition (P) ------------------- Availability (A)
    Tolerance         FlagShip: AP
```

### 2.1 The Architectural Decision: AP over CP

FlagShip explicitly chooses **Availability (A) + Partition Tolerance (P)** over strict linearizable Consistency (C):

1. **Catastrophic Impact of Remote RPC (CP Failure Mode)**:
   If flag evaluations were evaluated via synchronous remote HTTP/gRPC RPC calls to `flagship-server`, any network latency spike or server outage would immediately cascade across the entire microservice fleet. A transient 200 ms latency spike on the flag server would add 200 ms to *every* upstream customer request. If the server crashes, all connected microservices fail.

2. **The AP Design in FlagShip**:
   - **Local Evaluation**: Every client keeps an in-memory `AtomicReference<EnvironmentSnapshot>`.
   - **Zero Network Hop on Hot Path**: Flag evaluations complete in microseconds without issuing network I/O.
   - **Offline Autonomy**: If the server becomes unavailable or network partition occurs, client applications continue serving requests uninterrupted at full performance (verified in **QA2**).
   - **Local Persistence**: The starter persists the snapshot to a local file (`flagship-cache.json`), allowing clean cold restarts even when `flagship-server` is down.

### 2.2 Eventual Consistency Model

To reconcile state between the centralized server and distributed clients:
- **Server-Sent Events (SSE)**: The server pushes change deltas and full snapshot broadcasts over persistent HTTP streaming connections within milliseconds (verified in **QA3**: 50 clients updated in < 5 ms).
- **Periodic Polling Fallback**: If SSE connections disconnect, clients fall back to polling `/api/v1/projects/{proj}/environments/{env}/snapshot` every 30 seconds.
- **Monotonic Revision Counter**: Every snapshot carries an integer `revision` (version counter). Clients reject older snapshots, preventing stale rollbacks.
- **Convergence SLA**: Flag updates achieve network-wide eventual consistency across all running instances within **5.0 seconds** under normal conditions.

---

## 3. C4 Architecture Models

### 3.1 C4 Level 1: System Context Diagram

```mermaid
C4Context
    title System Context Diagram for FlagShip Platform

    Person(developer, "Software Engineer", "Configures feature flags, rules, and monitors rollouts")
    Person(operator, "Ops / Release Lead", "Approves production changes and triggers emergency kill switches")
    Person(enduser, "End User", "Interacts with business applications guarded by FlagShip")

    System_Boundary(flagship_boundary, "FlagShip Platform") {
        System(flagship, "FlagShip Platform", "Manages flag definitions, targeting rules, SSE live stream, and experimentation telemetry")
    }

    System_Ext(client_app, "Business Microservice", "Spring Boot microservices embedding flagship-spring-boot-starter")
    SystemDb_Ext(postgres, "PostgreSQL Database", "Relational persistence for audit logs, flags, segments, and revisions")

    Rel(developer, flagship, "Manages flags, segments, and views experiments via Admin UI / REST", "HTTPS")
    Rel(operator, flagship, "Approves production changes, triggers instant kill-switches", "HTTPS")
    Rel(client_app, flagship, "Streams live flag updates via SSE and fetches snapshots", "HTTP/SSE")
    Rel(client_app, flagship, "Pushes exposure and goal experimentation events", "HTTP POST")
    Rel(flagship, postgres, "Reads and writes relational flag data and audit trail", "JDBC/HikariCP")
    Rel(enduser, client_app, "Sends API / Web requests guarded by @FeatureGuard", "HTTPS")
```

---

### 3.2 C4 Level 2: Container Diagram

```mermaid
C4Container
    title Container Diagram for FlagShip Platform

    Person(engineer, "Engineer / Admin", "Configures flags and evaluates rollout metrics")

    Container_Boundary(c1, "FlagShip Infrastructure") {
        Container(admin_web, "Admin Web Console", "Thymeleaf, HTMX, Tailwind CSS, Spring Boot", "Interactive responsive management dashboard for flags, audit history, rollbacks, and experiments")
        Container(flagship_server, "FlagShip Server", "Spring Boot 4.x, Java 21 Virtual Threads, Spring MVC", "Control plane: CRUD REST APIs, Command history, RBAC, SSE broadcaster, and Experiment statistics")
        ContainerDb(database, "PostgreSQL Database", "PostgreSQL 16", "Stores projects, environments, flags, targeting rules, audit logs, and approval requests")
    }

    Container_Boundary(c2, "Client Application Instance") {
        Container(starter, "FlagShip Spring Boot Starter", "Java 21, Spring AOP, Actuator", "Embedded client library providing @FeatureGuard, SSE client, local cache, and disk persistence")
        Container(core_engine, "FlagShip Core Engine", "Pure Java 21, Zero Dependencies", "Deterministic MurmurHash3 bucketing, Composite/Visitor rule tree, and Chain of Responsibility evaluation")
    }

    Rel(engineer, admin_web, "Interacts with UI", "HTTPS")
    Rel(admin_web, flagship_server, "Invokes Management REST APIs", "JSON/HTTP")
    Rel(flagship_server, database, "Executes transactional queries and Flyway migrations", "JDBC")
    Rel(starter, flagship_server, "Subscribes to SSE stream / polls snapshot", "HTTP/SSE")
    Rel(starter, core_engine, "Executes in-memory flag evaluation", "In-Process Call (< 50 us)")
```

---

### 3.3 C4 Level 3: Component Diagrams

#### 3.3.1 FlagShip Server Components

```mermaid
C4Component
    title Component Diagram - FlagShip Server (Control Plane)

    Container_Boundary(server_boundary, "flagship-server") {
        Component(flag_ctrl, "FlagController", "Spring REST Controller", "REST CRUD endpoints for flags, variations, targeting rules, and kill-switches")
        Component(sse_ctrl, "SseStreamController", "Spring REST Controller", "Exposes SSE endpoint /api/v1/projects/{p}/environments/{e}/stream")
        Component(change_ctrl, "ChangeControlController", "Spring REST Controller", "Audit trail, rollback execution, and 2-person approval workflow")
        Component(exp_ctrl, "ExperimentationController", "Spring REST Controller", "Ingests exposure/goal events; calculates conversion, SRM chi-square, and z-test")
        Component(hygiene_ctrl, "FlagHygieneController", "Spring REST Controller", "Identifies stale flags (inactive > 30d, 100% rollout > 14d)")

        Component(cmd_service, "FlagCommandService", "Spring Service", "Command Pattern: executes mutations, records diffs, creates audit entries")
        Component(sse_service, "SseBroadcasterService", "Spring Service", "Virtual-thread broadcast pool distributing flag mutations to connected SSE emitters")
        Component(exp_service, "ExperimentationService", "Spring Service", "Statistical analysis engine (SRM test, two-sample Z-test)")
        Component(hygiene_service, "FlagHygieneService", "Spring Service", "Automated hygiene scanner detecting obsolete flags")

        Component(flag_repo, "FlagRepository", "Spring Data JPA", "Persists flag configurations and versions")
        Component(audit_repo, "AuditLogRepository", "Spring Data JPA", "Immutable append-only ledger of all configuration changes")
        Component(approval_repo, "ApprovalRequestRepository", "Spring Data JPA", "Tracks pending 2-person approvals for production changes")
    }

    Rel(flag_ctrl, cmd_service, "Dispatches flag commands")
    Rel(change_ctrl, cmd_service, "Rollback / approve commands")
    Rel(cmd_service, sse_service, "Triggers live SSE broadcast")
    Rel(cmd_service, flag_repo, "Persists flags")
    Rel(cmd_service, audit_repo, "Appends audit log")
    Rel(sse_ctrl, sse_service, "Registers SSE subscribers")
    Rel(exp_ctrl, exp_service, "Submits event batches")
    Rel(hygiene_ctrl, hygiene_service, "Runs hygiene report")
```

#### 3.3.2 FlagShip Core Engine Components

```mermaid
C4Component
    title Component Diagram - FlagShip Core (Pure Domain Engine)

    Container_Boundary(core_boundary, "flagship-core") {
        Component(engine, "FlagEvaluationEngine", "Evaluation Coordinator", "Coordinates evaluation pipeline using Chain of Responsibility")
        Component(chain, "EvaluationHandler Chain", "Chain of Responsibility", "KillSwitchHandler -> UserTargetingHandler -> SegmentRuleHandler -> PercentageRolloutHandler -> FallbackHandler")
        Component(rule_tree, "RuleNode Composite", "Composite Pattern", "LeafRuleNode (attribute comparison) and CompositeRuleNode (AND/OR logical branches)")
        Component(visitor, "EvaluationVisitor", "Visitor Pattern", "Traverses composite rule tree against EvaluationContext")
        Component(strategy, "OperatorStrategy", "Strategy Pattern", "EQUALS, IN, CONTAINS, SEMVER_GT, REGEX, DATE_AFTER, DATE_BEFORE")
        Component(hasher, "MurmurHash3Bucketer", "Strategy Pattern", "Deterministic 32-bit MurmurHash3 uniform distribution (0-10000 buckets)")
    }

    Rel(engine, chain, "Passes evaluation request through chain")
    Rel(chain, rule_tree, "SegmentRuleHandler invokes rule tree")
    Rel(rule_tree, visitor, "Accepts EvaluationVisitor")
    Rel(visitor, strategy, "Delegates operator comparison")
    Rel(chain, hasher, "PercentageRolloutHandler invokes hashing")
```

---

## 4. Architectural Boundaries and Module Layering

To prevent framework leaks and ensure rigorous compliance with constraint **C6**, FlagShip enforces strict module layering via ArchUnit tests:

| Module | Allowed Runtime Dependencies | Forbidden Dependencies | Rationale |
| :--- | :--- | :--- | :--- |
| `flagship-core` | Pure Java 21 Standard Library | Spring, Jackson, Hibernate, Jakarta EE | Guarantee portability, zero reflection overhead, sub-50µs latency |
| `flagship-server` | Spring Boot 4.x, Spring Data JPA, Flyway, PostgreSQL | None | Enterprise-grade control plane persistence and security |
| `flagship-spring-boot-starter` | `flagship-core`, Spring Boot Starter, Spring AOP, Actuator | `flagship-server` JPA/Flyway entities | Decoupled client integration without pulling database drivers |
| `flagship-admin-web` | Spring Boot MVC, Thymeleaf, HTMX | None | Server-side rendered lightweight administrative UI |
| `flagship-demo-app` | `flagship-spring-boot-starter` | None | Demonstrates clean consumer-side starter adoption |
