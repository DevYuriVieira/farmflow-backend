# ADR-005: W3C Trace Context Propagation Across Async Boundaries

## Status
Accepted

## Context
Understanding end-to-end latency and causality across HTTP, relational database outbox polling, RabbitMQ exchanges, and asynchronous consumers is critical for telemetry and incident triage.

## Decision
We enforce W3C Trace Context (`traceparent` header). The incoming trace context is captured in `CorrelationIdFilter`, stored in `outbox_messages.trace_parent`, injected into AMQP message headers, and restored in downstream consumers for Jaeger / OpenTelemetry collection.

## Consequences
- **Positive:** Complete distributed tracing waterfall across all async boundaries.
- **Positive:** Trace IDs correlate log lines and RFC 9457 error responses.