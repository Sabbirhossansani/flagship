# ADR-001: Client-Side Local Evaluation vs Remote Server RPC

## Status
Accepted

## Date
2026-11-24

## Context
In modern microservice architectures, applications evaluate feature flags hundreds or thousands of times during a single user request (e.g., checking user targeting on product lists, payment gateways, button styling, and experimental algorithms). 

We evaluated how the client applications should resolve flag decisions:
1. **Option A: Remote RPC / REST Evaluation**: Every `client.isEnabled("flagKey", ctx)` invocation sends a synchronous HTTP/gRPC request to `flagship-server`.
2. **Option B: Edge / Sidecar Proxy Evaluation**: Every application talks to a localhost sidecar daemon (e.g., Envoy or a Go agent).
3. **Option C: In-Process Client-Side Local Evaluation**: The client library maintains a synchronized in-memory snapshot of all flag rules and evaluates rules entirely in-process using pure Java domain logic.

## Decision
We chose **Option C: In-Process Client-Side Local Evaluation**.

FlagShip distributes flag configurations to consuming applications as immutable environment snapshots. All targeting rules, user bucketing, and variation lookups are computed inside the application's JVM memory space via `flagship-core`.

## Consequences
### Positive
- **Near-Zero Latency**: Flag evaluation takes microseconds rather than milliseconds, eliminating network overhead on the critical request path.
- **Extreme High Availability (AP)**: Outages of `flagship-server` or database do not impact running microservices.
- **Reduced Infrastructure Costs**: The central flag server does not need to handle millions of evaluation requests per second; it only serves light synchronization streams.

### Negative
- **Memory Footprint**: The client JVM must retain the environment snapshot in heap memory.
- **Eventual Consistency**: Flag mutations made on the server take a brief propagation window (< 5 seconds) to reach all client instances.

## Measured Evidence
- **QA1 Performance Benchmark**:
  - Evaluation latency for a flag with 10 complex targeting rules (AND/OR composites, regex, semantic versioning) measured at:
    - **p50**: 1.20 µs
    - **p95**: 3.50 µs
    - **p99**: 7.10 µs
  - Compared to a typical remote network RPC (10–25 ms = 10,000–25,000 µs), client-side evaluation is **over 1,400x faster**.
  - Retained heap footprint for 1,000 loaded flags measured at **5.38 MB**, strictly well below the 20.0 MB SLA.
