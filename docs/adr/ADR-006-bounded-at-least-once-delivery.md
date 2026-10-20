# ADR-006: Bounded At-Least-Once Delivery and Consumer Idempotency

## Status
Accepted

## Context
Because network partitions can occur after a consumer commits a transaction but before acknowledging the broker, message redelivery (`Redelivered == true`) is guaranteed by RabbitMQ. Consumers must ensure that redelivered messages never duplicate business effects.

## Decision
We implement consumer deduplication via `MessageDeduplicationService`. The consumer verifies `message_id` existence in `processed_messages` with a primary key constraint before executing domain logic. Duplicate deliveries are immediately acknowledged without re-execution.

## Consequences
- **Positive:** Single business effect within the audited transaction boundary.
- **Positive:** Protection against duplicate pest orders or duplicate telemetry alerts.