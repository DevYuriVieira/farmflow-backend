# ADR-011: Operational Metrics and Telemetry Cardinality Discipline

## Status
Accepted

## Context
High-cardinality metrics (such as embedding user IDs, raw timestamps, or dynamic query strings in metric tags) cause severe memory leaks and metric database bloat.

## Decision
We implement `FarmFlowMetrics` utilizing Micrometer instruments with strictly bounded cardinality:
- Counters: `farmflow.outbox.messages.published`, `farmflow.outbox.messages.failed`.
- Gauges: `farmflow.outbox.backlog.count`.
- Tagged Counters: `farmflow.consumer.messages.processed` and `farmflow.consumer.messages.failed` tagged solely with finite `event_type` strings (`TelemetryAlert`, `PestDetected`, `MachineryMaintenanceScheduled`).

## Consequences
- **Positive:** Predictable memory footprint and bounded dimension keys in Prometheus / Actuator.
- **Positive:** Clear visibility into outbox publishing rate and consumer throughput.