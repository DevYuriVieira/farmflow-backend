# ADR-019: Atomic Delta Accumulation for Machinery Operating Hours

## Status
Accepted

## Context
Machinery operating hours (horímetro) track engine running time for maintenance scheduling and depreciation accounting. When multiple field activities or automated telemetry streams report equipment usage concurrently, a naive read-modify-write pattern (`findById` -> `hours = current + delta` -> `save()`) causes lost updates: concurrent transactions read the same initial value and overwrite each other's increments. Adding optimistic locking with optimistic concurrency exceptions (`ObjectOptimisticLockingFailureException`) leads to high transaction abort rates and consumer retry storms under high load.

## Decision
We replace read-modify-write loops with database-level atomic delta accumulation:
1. **Single-Statement Atomic Increment:** `MachineryRepository.incrementOperatingHoursAtomic` executes:
   ```sql
   UPDATE machinery
   SET operating_hours = COALESCE(operating_hours, 0.0) + :delta,
       version = version + 1,
       updated_at = NOW()
   WHERE id = :id AND farm_id = :farmId AND :delta > 0.0
   ```
2. **Zero In-Memory Read on the Hot Path:** `ActivityCompletedMachineryConsumer` removes `findById` completely. The consumer validates numeric constraints (`hoursUsed > 0 && hoursUsed <= 24`) and invokes the atomic delta query directly.
3. **Poison and DLQ Handling:** If 0 rows are updated, the consumer distinguishes between missing machinery / tenant divergence and routes poison payloads to the DLQ (`farmflow.events.dlq`) instead of dropping the event silently.
4. **Tenant Defense-in-Depth:** The update query explicitly includes `farm_id = :farmId` alongside the database-level Row Level Security barrier.

## Consequences
- **Positive:** Zero lost updates: concurrent events accumulate hours with exact arithmetic precision, verified under concurrent multithreaded test suites (`CountDownLatch`).
- **Positive:** Maximum throughput: row lock duration is confined to the single microsecond `UPDATE` statement execution in PostgreSQL.
- **Positive:** Immune to optimistic locking retry loops in AMQP consumers.
- **Negative:** In-memory cached representations of `Machinery` must be refreshed if subsequent business logic in the same transaction requires the post-increment total.
