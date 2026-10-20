# ADR-010: Service Level Objectives (SLOs) and Error Budget Framework

## Status
Accepted

## Context
Staff-level production engineering requires objective, mathematically grounded reliability metrics rather than vague "high availability" promises.

## Decision
We formalize SLIs, SLOs, and monthly Error Budgets for FarmFlow API:
- **Synchronous Write Availability:** >= 99.9% uptime (<= 43.2 min monthly downtime).
- **Synchronous Write Latency:** P95 <= 250ms on all `/api/*` endpoints.
- **Outbox Relay Latency:** P95 <= 5.0s between `occurred_on` and broker publication.
- **Consumer Processing Latency:** P95 <= 100ms from dispatch to deduplication commit.

## Consequences
- **Positive:** Clear alerting thresholds and error budget burn rate triggers.
- **Positive:** Clear engineering accountability for feature releases vs. reliability hardening.