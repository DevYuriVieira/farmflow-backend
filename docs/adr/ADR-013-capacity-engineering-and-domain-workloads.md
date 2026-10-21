# ADR-013: Capacity Engineering, Domain-Level Workloads, and Performance Boundary Validation

## Status
Accepted

## Context
Benchmarking diagnostic probes (`/health/live`) does not represent real-world transactional capacity involving relational persistence, version checks, and outbox insertion.

## Decision
We explicitly demarcate:
1. In-memory diagnostic probe capacity (Kestrel/Spring MVC thread limits).
2. Domain transactional write capacity (Hikari connection pool hold duration, PostgreSQL MVCC transactions, and Outbox writes).

## Consequences
- **Positive:** Prevents unrealistic capacity extrapolations.
- **Positive:** Clarifies bottlenecks around database connection pool exhaustion under concurrency.