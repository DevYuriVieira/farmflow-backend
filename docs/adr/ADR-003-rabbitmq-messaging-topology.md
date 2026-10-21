# ADR-003: RabbitMQ Messaging Topology with Staged TTL Retries and DLQ

## Status
Accepted

## Context
Transient failures (network partitions, downstream timeouts) are normal in distributed agro-industrial systems. Issuing unbounded `nack(requeue: true)` saturates CPU and deadlocks message queues.

## Decision
We implement a resilient RabbitMQ messaging topology with staged retries and diagnostic DLQ:
- **Main Topic Exchange:** `farmflow.events`
- **Dead-Letter Exchange (DLX):** `farmflow.events.dlx` (Direct)
- **Work Queues:**
  - `telemetry.alerts.queue` (binding: `telemetry.alert.#`)
  - `pest.detections.queue` (binding: `pest.detected.#`)
  - `machinery.maintenance.queue` (binding: `machinery.maintenance.#`)
  - `activity.events.queue` (binding: `activity.#`)
- **Staged Native TTL Retry Queues (Multi-Flow Bound):**
  - `farmflow.events.retry.10s` (TTL: 10,000ms) with dead-letter back to `farmflow.events`
  - `farmflow.events.retry.30s` (TTL: 30,000ms) with dead-letter back to `farmflow.events`
  - `farmflow.events.retry.90s` (TTL: 90,000ms) with dead-letter back to `farmflow.events`
  - Explicit bindings exist for all flows (`telemetry.alerts.retry.*`, `pest.detected.retry.*`, `machinery.maintenance.retry.*`, `activity.events.retry.*`).
- **Dead-Letter Queue (DLQ):** `farmflow.events.dlq` preserves diagnostic headers (`x-first-death-reason`, `x-death-count`, `x-original-queue`, `x-failure-reason`) for dead-letter analysis.

## Consequences
- **Positive:** Multi-stage native exponential backoff without application thread blocking or hot requeue loops.
- **Positive:** Every agro-industrial event flow (telemetry, pest, machinery, activities) is fully routed through the retry ladder without message loss.
- **Positive:** Malformed/poison messages bypass retries and land in the DLQ with forensic headers for operator inspection.