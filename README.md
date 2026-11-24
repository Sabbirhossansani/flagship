# FlagShip: Feature-Flag & Gradual-Rollout Platform

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Java Version](https://img.shields.io/badge/Java-21%20LTS-blue.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.10-green.svg)]()
[![Architecture](https://img.shields.io/badge/Architecture-C4%20%7C%20AP%20System-orange.svg)]()

> **FlagShip** is a high-performance, enterprise-grade feature flagging and gradual-rollout platform paired with an idiomatic Spring Boot Starter. It brings sub-50-microsecond local evaluations, resilient offline operation, real-time SSE push updates, deterministic monotonic rollouts, and multi-user change governance to modern distributed microservices.

---

## 1. System Architecture & Highlights

FlagShip decouples **centralized configuration management** from **in-process runtime evaluation**:
- **Pure Java 21 Core (`flagship-core`)**: Zero framework dependencies (no Spring, no Jackson, no JPA). Evaluates complex nested rule trees in **< 10 microseconds** at p99.
- **Spring Boot 4.x Server (`flagship-server`)**: PostgreSQL 16 relational control plane with Flyway migrations, REST CRUD, virtual-thread SSE broadcaster, 2-person production approvals, one-action rollback, and statistical A/B test analysis (SRM chi-square & two-proportion z-tests).
- **Spring Boot Starter (`flagship-spring-boot-starter`)**: Embedded client library with `@FeatureGuard` AOP aspect, in-memory caching with `AtomicReference`, local disk persistence (`flagship-cache.json`), SSE streaming with polling fallback, and `/actuator/flagship` endpoint.
- **Admin Web Console (`flagship-admin-web`)**: Modern, responsive dashboard (Thymeleaf, HTMX, Tailwind CSS) for managing projects, environments, flags, targeting rules, approval requests, audit logs, and stale flags.
- **Demo Application (`flagship-demo-app`)**: Reference microservice showing zero-friction starter integration.

---

## 2. Quick-Start Guide (< 15 Minutes)

### Prerequisites
- **Java 21 LTS** (`java -version`)
- **Apache Maven 3.9+** (`mvn -version`)
- **Docker & Docker Compose** (Optional, for containerized run)

### Step 1: Clone & Build
```powershell
# Set Java 21 environment
$env:JAVA_HOME = 'C:\Users\Sani\.tools\jdk21'
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\Sani\.tools\maven\bin;$env:PATH"

# Clean build and run all unit and architecture tests
mvn clean install
```

### Step 2: Run via Docker Compose (Recommended)
Launch the entire platform (PostgreSQL, Control Plane, Admin Web, and Demo App) with a single command:
```powershell
docker-compose up --build -d
```
All services will start in under 60 seconds:
- **Admin Web Console**: [http://localhost:8081](http://localhost:8081)
- **FlagShip Server REST API**: [http://localhost:8080](http://localhost:8080)
- **OpenAPI Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Demo Microservice**: [http://localhost:8082](http://localhost:8082)
- **Demo Actuator FlagShip Endpoint**: [http://localhost:8082/actuator/flagship](http://localhost:8082/actuator/flagship)

### Step 3: Run Locally from IDE or Maven
Alternatively, launch services individually:
```powershell
# 1. Start Server
mvn spring-boot:run -pl flagship-server

# 2. Start Admin Web
mvn spring-boot:run -pl flagship-admin-web

# 3. Start Demo App
mvn spring-boot:run -pl flagship-demo-app
```

---

## 3. How to Use `flagship-spring-boot-starter` in Your Application

### 1. Add Maven Dependency
```xml
<dependency>
    <groupId>com.flagship</groupId>
    <artifactId>flagship-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure `application.yml`
```yaml
flagship:
  server-url: http://localhost:8080
  project-key: default-project
  environment-key: production
  api-key: dev-key-123
  sse-enabled: true
  polling-interval-seconds: 30
  disk-persistence-enabled: true
  disk-persistence-path: /tmp/flagship-cache.json
```

### 3. Guard Methods with `@FeatureGuard`
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/v2/checkout")
    @FeatureGuard(flagKey = "instant-checkout-v2", fallbackMethod = "legacyCheckout")
    public String v2Checkout() {
        return "Processed via Instant Checkout V2!";
    }

    public String legacyCheckout() {
        return "Processed via Legacy Checkout (Fallback)";
    }
}
```

### 4. Or Query Programmatically
```java
@Autowired
private FlagShipClient flagshipClient;

public void processPayment(String userId, String country) {
    EvaluationContext context = EvaluationContext.builder(userId)
            .attribute("country", country)
            .build();

    if (flagshipClient.isEnabled("stripe-payment-v2", context, false)) {
        // execute new gateway
    } else {
        // execute default gateway
    }
}
```

---

## 4. Automated Quality Attribute Verification (QA1–QA5)

FlagShip includes a fully automated verification test suite in `scripts/qa/`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run-all-qa.ps1
```

### Measured Benchmark Results

| Metric | Target SLA | Measured Result | Status |
| :--- | :--- | :--- | :--- |
| **QA1 (Speed)** | p99 $\le$ 50.0 µs | **p99: 7.10 µs** (p50: 1.20 µs) | **PASSED** |
| **QA1 (Memory)**| Heap $\le$ 20.0 MB / 1k flags | **5.38 MB** | **PASSED** |
| **QA2 (Outage)**| 30-min blackout resilience | **10,000/10,000 requests served, 0 exceptions** | **PASSED** |
| **QA3 (Propagation)**| 50 clients updated in < 5.0 s | **5 milliseconds** | **PASSED** |
| **QA4 (Consistency)**| 1M user keys determinism & monotonic ramp | **0 mismatches, 0 monotonicity violations** | **PASSED** |
| **QA5 (Bootstrap)**| Source to packaging & verify in < 15 min | **43.0 seconds** | **PASSED** |

---

## 5. Architectural Documentation & Evidence Catalog

Comprehensive design documentation is available in the [`docs/`](docs/) directory:
- [**C4 Architecture & CAP Analysis**](docs/ARCHITECTURE.md): Context, Container, Component diagrams, and AP vs CP trade-off analysis.
- [**Design Patterns Justification Table**](docs/PATTERNS.md): Composite, Visitor, Chain of Responsibility, Strategy, Observer, Proxy/Decorator, Command, Builder, and Singleton position.
- [**Mini-ATAM Utility Tree**](docs/MINI_ATAM.md): 6+ operational scenarios, sensitivity points, and trade-off points.
- [**Evidence Report (C9)**](docs/EVIDENCE_REPORT.md): Deep QA1–QA5 analysis, raw benchmark outputs, visual charts, and failed-run engineering fixes.
- [**AI Usage Log (C11)**](docs/AI_USAGE_LOG.md): Incremental development record, prompts, tools, and 3 detailed error diagnosis case studies.
- **Architecture Decision Records (ADRs)**:
  - [ADR-001: Client-Side Local Evaluation vs Remote RPC](docs/adr/ADR-001-client-side-local-evaluation.md)
  - [ADR-002: Real-Time SSE Streaming with Polling Fallback](docs/adr/ADR-002-sse-realtime-streaming.md)
  - [ADR-003: 32-bit MurmurHash3 Sticky Rollouts](docs/adr/ADR-003-murmurhash3-sticky-rollouts.md)
  - [ADR-004: In-Memory Snapshots with Local Disk Persistence](docs/adr/ADR-004-offline-resilience-local-persistence.md)
