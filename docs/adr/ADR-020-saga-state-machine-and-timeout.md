# ADR-020: Choreographed Saga State Machine and Timeout Compensation for Cross-Domain Workflows

## Status
Accepted

## Context
When an agricultural field activity (planting, spraying, harvesting) is executed, it requires input reservations from the Supply/Inventory domain (fertilizers, seeds, pesticides). In a distributed architecture, input reservation can fail due to out-of-stock conditions or network partitions. In previous implementations, consumer handlers concatenated descriptive strings to free-form `notes` without deterministic state machine transitions, leaving activities permanently stuck in limbo without compensating actions, structured audit trails, or timeout handling.

## Decision
We implement a choreographed Saga pattern governed by a strict aggregate state machine, immutable audit logs, and timeout sweeps:
1. **Explicit Domain State Machine:** `FarmActivity` introduces enum `ActivityStatus`:
   - `PLANNED` -> `IN_PROGRESS` -> `PENDING_CONFIRMATION` -> `COMPLETED`.
   - On reservation failure or timeout: `PENDING_CONFIRMATION` -> `CANCELLED` (or compensated state).
   - Domain invariants are encapsulated in aggregate methods (`confirmInputReservation()`, `compensateInputReservation(reason)`), rejecting illegal transitions via `IllegalStateException`.
2. **Elimination of Notes as State:** Concatenating operational state strings to `notes` is strictly prohibited and enforced via ArchUnit.
3. **Structured Saga Audit Logging:** Table `saga_audit_logs` records every saga step transition (`correlation_id`, `saga_type`, `step_name`, `status`, `payload`, `failure_reason`), providing complete traceability.
4. **Reliable Outbox Events:** Consumers publish `com.farmflow.activity.confirmed.v1` or `com.farmflow.activity.compensated.v1` via the transactional outbox in the same ACID boundary.
5. **Automated Saga Timeout Sweeper:** `SagaTimeoutService` runs periodically, sweeping activities stuck in `PENDING_CONFIRMATION` past the threshold (15 minutes), executing compensation, recording audit logs, and incrementing Prometheus metric `farmflow.saga.timeout.total`.

## Consequences
- **Positive:** Deterministic lifecycle: cross-domain activities never remain stuck in unconfirmed states.
- **Positive:** Eventual consistency with compensation: downstream inventory reservations released on failure.
- **Positive:** Full audit compliance: every state transition is recorded in dedicated audit entities rather than unstructured text columns.
- **Negative:** Event out-of-order delivery must be guarded by optimistic locking (`@Version`) and idempotent deduplication in consumers.
