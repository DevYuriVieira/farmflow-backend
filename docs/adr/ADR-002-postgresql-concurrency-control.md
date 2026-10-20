# ADR-002: PostgreSQL Concurrency Control via SKIP LOCKED and Optimistic Locking

## Status
Accepted

## Context
When running multiple replicas of the backend API in Kubernetes or container clusters, multiple `OutboxProcessor` workers concurrently poll the `outbox_messages` table. Without row-level locking controls, concurrent replicas would select identical pending batches, causing duplicate publications and lock contention.

## Decision
We implement pessimistic row-level locking using PostgreSQL's `FOR UPDATE SKIP LOCKED`:
```sql
SELECT * FROM outbox_messages
WHERE processed_on IS NULL AND attempts < :maxAttempts
ORDER BY occurred_on
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```
For domain entities (`AuditableEntity`), optimistic concurrency control is enforced via JPA `@Version` tokens.

## Consequences
- **Positive:** Contention-free concurrent polling (`lock_wait = 0ms`). Each replica dynamically processes a disjoint batch without blocking other active workers.
- **Positive:** Automatic fault isolation: if a replica crashes mid-execution, PostgreSQL automatically releases the locks upon transaction abort.
- **Negative:** Requires careful index maintenance (`ix_outbox_unprocessed`) on `occurred_on WHERE processed_on IS NULL`.