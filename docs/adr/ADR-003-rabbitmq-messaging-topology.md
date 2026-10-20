# ADR-003: RabbitMQ Messaging Topology with Staged TTL Retries and DLQ

## Status
Accepted

## Context
Transient failures (network partitions, downstream timeouts) are normal in distributed agro-industrial systems. Issuing unbounded `nack(requeue: true)` saturates CPU and deadlocks message queues.

## Decision
We implement a resilient RabbitMQ messaging topology:
- **Main Topic Exchange:** `farmflow.events`
- **Dead-Letter Exchange (DLX):** `farmflow.events.dlx` (Direct)
- **Staged Native TTL Retry Queues:**
  - `farmflow.events.retry.10s` (TTL: 10,000ms)
  - `farmflow.events.retry.30s` (TTL: 30,000ms)
  - `farmflow.events.retry.90s` (TTL: 90,000ms)
- **Dead-Letter Queue (DLQ):** `farmflow.events.dlq` for poison messages and exhausted retries.

## Consequences
- **Positive:** Clean exponential backoff without application-level sleep threads.
- **Positive:** Malformed payloads (poison messages) are immediately routed to the DLQ with diagnosis headers (`x-dlq-reason`), preventing queue blockages.