# ADR-002: Real-Time Synchronization via Server-Sent Events (SSE) with Polling Fallback

## Status
Accepted

## Date
2026-11-24

## Context
Because flag evaluations happen locally in-process (ADR-001), changes initiated by operators (such as toggling an emergency kill-switch or expanding a percentage rollout) must propagate to all running microservice instances in real time (FR5, QA3).

We evaluated three synchronization mechanisms:
1. **Option A: Periodic Polling Only**: Clients periodically query `GET /api/v1/.../snapshot` every 3–5 seconds.
2. **Option B: Full-Duplex WebSockets**: Clients maintain bi-directional WebSocket connections to `flagship-server`.
3. **Option C: Server-Sent Events (SSE) with Periodic Polling Fallback**: Server streams uni-directional text events over standard HTTP/1.1 or HTTP/2 connections, backed by Java 21 virtual threads, with periodic polling (30s) if disconnected.

## Decision
We chose **Option C: Server-Sent Events (SSE) with Periodic Polling Fallback**.

SSE is a standardized, lightweight, uni-directional streaming protocol running over standard HTTP. Since clients only need to receive updates from the server and do not need to send bidirectional binary frames over the sync channel, SSE avoids the protocol complexity and proxy/firewall friction of WebSockets. Virtual threads in Spring Boot (`Executors.newVirtualThreadPerTaskExecutor()`) allow the server to hold tens of thousands of idle SSE connections with negligible memory overhead.

## Consequences
### Positive
- **Sub-Second Fan-Out**: Flag updates reach connected clients in single-digit milliseconds.
- **Firewall & Proxy Compatibility**: Works over standard HTTP ports (80/443) without specialized WebSocket upgrade handshakes.
- **Built-in Resilience**: If the SSE stream terminates, the client automatically triggers exponential backoff reconnects while continuing periodic HTTP polling.

### Negative
- **Persistent HTTP Sockets**: Each running client instance holds an open HTTP socket to the server.
- **Connection Limits**: Reverse proxies (such as Nginx) require proper buffering configuration (`proxy_buffering off;`).

## Measured Evidence
- **QA3 Kill-Switch Propagation Test**:
  - Broadcasted an emergency kill-switch mutation to **50 concurrent connected client instances**.
  - All 50 clients received and applied the kill switch in **5 milliseconds**.
  - This strictly surpasses the Quality Attribute requirement of **< 5.0 seconds at p95** by three orders of magnitude.
