# ADR-001: Transactional Outbox Pattern for Reliable Event Publishing

## Status
Accepted

## Context
In an agro-industrial operations platform, domain events such as critical telemetry alerts, pest outbreak detections, and machinery maintenance orders must be published reliably. In a naive approach (dual-write hazard), persisting entity state to PostgreSQL and publishing directly to a messaging broker (RabbitMQ) within the same HTTP request is prone to message loss if the network fails or the JVM restarts post-commit.

## Decision
We implement the Transactional Outbox Pattern. When a domain entity is created or modified, an `OutboxMessage` record is inserted into the `outbox_messages` table within the **same relational ACID transaction**. An asynchronous background worker (`OutboxProcessor`) periodically queries pending messages and dispatches them to RabbitMQ using Publisher Confirms.

## Consequences
- **Positive:** Zero message loss (*at-least-once delivery* guarantee). Eliminates dual-write hazard between PostgreSQL and RabbitMQ.
- **Positive:** Write availability is preserved even when RabbitMQ is completely offline; commands continue to commit to PostgreSQL and messages accumulate in the outbox.
- **Negative:** Downstream consumers must be strictly idempotent to handle potential redeliveries safely.