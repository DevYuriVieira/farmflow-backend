# ADR-001: Transactional Outbox Pattern for Reliable Event Publishing

## Status
Accepted

## Context
In an agro-industrial operations platform, domain events such as critical telemetry alerts, pest outbreak detections, and machinery maintenance orders must be published reliably. In a naive approach (dual-write hazard), persisting entity state to PostgreSQL and publishing directly to a messaging broker (RabbitMQ) within the same HTTP request is prone to message loss if the network fails or the JVM restarts post-commit.

## Decision
We implement the Transactional Outbox Pattern with Lease Claim-Check and Explicit State Transitions:
1. When a domain entity is created or modified, an `OutboxMessage` record is inserted into the `outbox_messages` table within the **same relational ACID transaction**.
2. Each outbox record has an explicit status lifecycle: `PENDING` -> `CLAIMED` -> `PUBLISHED` | `RETRY_WAIT` -> `DEAD`.
3. An asynchronous worker (`OutboxProcessor`) claims batches using `SELECT ... FOR UPDATE SKIP LOCKED` and sets `claimed_by` and `claimed_until` (lease). If a relay worker crashes, expired leases are safely reclaimed by surviving instances.
4. Transient publish failures trigger exponential backoff with jitter (`next_attempt_at`), preventing retry storms during broker unavailability.
5. Messages that exhaust all attempts transition to `DEAD` state, exposing `farmflow.outbox.dead.count` and triggering operator runbooks.
6. The relay monitors message age via `farmflow.outbox.oldest.message.age.seconds` and latency via `farmflow.outbox.publish.duration` timer.

## Consequences
- **Positive:** Zero message loss (*at-least-once delivery* guarantee). Eliminates dual-write hazard between PostgreSQL and RabbitMQ.
- **Positive:** Multi-instance concurrency safe via row-level lease claims (`claimed_until`), preventing duplicate broker dispatches across replicas.
- **Positive:** Write availability is preserved even when RabbitMQ is completely offline; commands continue to commit to PostgreSQL and messages accumulate in the outbox.
- **Positive:** Proactive SRE observability through outbox lag and dead-letter gauges.
- **Negative:** Downstream consumers must implement transactional inbox deduplication (`processed_messages` ON CONFLICT DO NOTHING) to safely handle redeliveries.