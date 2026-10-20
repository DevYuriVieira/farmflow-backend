# ADR-009: Decoupled Health Probing and Graceful Degradation Semantics

## Status
Accepted

## Context
Kubernetes and container orchestrators need accurate liveness, readiness, and dependency diagnostic probes. If an orchestrator kills an API instance merely because a messaging broker is restarting, it causes cascading failure across the cluster.

## Decision
We decouple health probes in `HealthProbeController`:
- **`/health/live`:** Ultra-lightweight in-memory check (no external I/O).
- **`/health/ready`:** Evaluates PostgreSQL connectivity (`200 OK` or `503 Unavailable`).
- **`/health/dependencies`:** Multi-dependency inspection. Returns `HEALTHY` when all respond, `DEGRADED` (HTTP 200) when RabbitMQ is offline (since writes continue safely via Transactional Outbox), and `UNHEALTHY` (503) only when PostgreSQL is offline.

## Consequences
- **Positive:** Prevents unnecessary pod restarts during downstream broker maintenance.
- **Positive:** Accurate monitoring of transactional decoupling.