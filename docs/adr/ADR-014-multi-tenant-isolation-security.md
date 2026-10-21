# ADR-014: Multi-Tenant Isolation and Farm Security Boundaries

## Status
Accepted

## Context
In an agro-industrial SaaS platform, farms and plantations must maintain strict tenant isolation. Users belonging to Farm A must never access or mutate data of Farm B.

## Decision
We enforce multi-tenant isolation via `FarmSecurityEvaluator` and Spring Security `@EnableMethodSecurity`. Service and controller methods validate user farm affiliation on all mutations and reads.

## Consequences
- **Positive:** Data privacy and zero accidental cross-farm data leakage.
- **Positive:** Declarative security evaluated before query execution.