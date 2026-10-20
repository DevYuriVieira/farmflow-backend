# ADR-004: PostgreSQL as Sole Primary Coordination Engine

## Status
Accepted

## Context
Adding Redis or external caching engines for state coordination introduces an additional source of dual-write hazard and operational complexity.

## Decision
We leverage PostgreSQL 17/18 as the single source of truth and coordination engine. ACID transactions, optimistic versioning, physical check constraints, and partial unique indexes (`ix_machinery_active_maintenance`, `ix_pest_records_recent`) provide complete concurrency coordination.

## Consequences
- **Positive:** Zero cache-invalidation bugs and zero distributed consensus overhead.
- **Positive:** Reduced infrastructure footprint in development, testing, and production.