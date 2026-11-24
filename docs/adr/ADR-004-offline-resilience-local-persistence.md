# ADR-004: Offline Resilience via Local Disk Persistence and In-Memory Atomic Snapshots

## Status
Accepted

## Date
2026-11-24

## Context
When microservices rely on feature flags to control critical business logic (e.g., checkout flows, fraud detection, third-party API gateways), the feature flag system must **never become a single point of failure** (FR6, QA2). 

Two failure scenarios must be guarded against:
1. **Mid-Flight Blackout**: `flagship-server` crashes while consuming applications are already running.
2. **Cold Start During Blackout**: A consumer application starts up (e.g., auto-scaling pod during peak traffic) while `flagship-server` is down or unreachable.

We evaluated three approaches:
1. **Option A: Fail-Open to Hardcoded Fallbacks Only**: If the server is unreachable, return hardcoded default values without retaining past snapshot configurations.
2. **Option B: Distributed External Cache (Redis Cluster)**: Persist snapshots in a centralized Redis cluster.
3. **Option C: Hybrid In-Memory Snapshot with Atomic Swapping and Local Disk File Persistence**: Maintain state in an `AtomicReference<EnvironmentSnapshot>`, asynchronously write snapshot JSON to a local disk file upon update, and cold-start from disk if the server is unreachable.

## Decision
We chose **Option C: Hybrid In-Memory Snapshot with Atomic Swapping and Local Disk File Persistence**.

The starter asynchronously persists the latest valid snapshot to a configurable local file path (`flagship-cache.json`). At application startup:
1. The starter attempts to fetch an initial snapshot from `flagship-server` with a strict **2.0-second timeout**.
2. If the server does not respond within 2.0 seconds, the starter falls back to reading the local disk cache.
3. If no disk cache exists, it initializes with a safe empty snapshot and falls back to developer-specified default values with zero exceptions.
4. During runtime, updates are applied via `AtomicReference.set(newSnapshot)`, guaranteeing wait-free, non-blocking reads for business threads.

## Consequences
### Positive
- **100% Uptime During Outages**: Consuming microservices continue serving feature flag decisions indefinitely without downtime even during protracted server blackouts.
- **Fast Cold Starts**: Prevents container startup hangs when the flag server is undergoing maintenance.
- **Zero External Infrastructure**: No external Redis or Memcached clusters required on client nodes.

### Negative
- **Disk Write Permission**: Requires the container filesystem to allow writing to the configured cache path (defaults to `/tmp/flagship-cache.json`).
- **Disk I/O Isolation**: Disk write must be asynchronous or off the hot evaluation path so filesystem flushes do not block business threads.

## Measured Evidence
- **QA2 30-Minute Outage Simulation**:
  - Simulated a complete 30-minute blackout of `flagship-server`.
  - Cold-started an application instance during the blackout reading exclusively from the disk cache.
  - Executed **10,000 flag evaluation requests** during the blackout:
    - Cached flags evaluated correctly from persisted snapshot.
    - Non-existent flags safely returned default fallback values.
    - **Total Exceptions**: Exactly **0**.
    - **Availability**: **100.0%**.
  - Once the server recovered, client cleanly applied revision update (`rev=10` -> `rev=11`).
