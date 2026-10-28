# ADR-017: Fail-Closed Defense-in-Depth Multi-Tenancy via PostgreSQL Row Level Security (RLS)

## Status
Accepted

## Context
Multi-tenant isolation in agro-industrial operations must prevent cross-tenant data leakage under all failure scenarios, including software bugs in application query layers, ORM misconfigurations, or compromised JWT contexts. Prior implementations in V13 used permissive policies with `'BYPASS'` strings and `OR current_setting(...) IS NULL`, creating fail-open vulnerabilities where missing context exposed all tenants' sensitive operational records. Furthermore, running the application with the table owner role bypassed RLS policies unless explicitly forced.

## Decision
We enforce strict, fail-closed PostgreSQL Row Level Security across all domain tables with non-owner role separation:
1. **Fail-Closed Policy Definition:** Policies across `farms`, `plantations`, `activities`, `inventory_items`, `machinery`, `pest_records`, `telemetry_readings`, and `saga_audit_logs` enforce:
   `USING (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''))`
   and an identical `WITH CHECK` clause. An unauthenticated connection or an empty context string yields `NULL`, evaluating to false and returning zero rows.
2. **FORCE ROW LEVEL SECURITY:** `ALTER TABLE ... FORCE ROW LEVEL SECURITY` is applied to all tenant tables, preventing accidental policy bypass even when accessed by table owners.
3. **Application Role Separation & Credential Isolation:**
   - The application runtime connects strictly as `farmflow_app` (`NOSUPERUSER NOBYPASSRLS`), configured in `spring.datasource.username`.
   - Structural schema migrations execute via Flyway under administrative credentials (`spring.flyway.user: postgres`).
   - Default privileges (`ALTER DEFAULT PRIVILEGES`) guarantee DML access on future tables without superuser privileges.
4. **Context Propagation, Fail-Closed Aspect & Anti-Contamination Guard:**
   - `TenantRlsAspect` injects `SELECT set_config('app.current_farm_id', :id, true)` at the transactional boundary, deduplicating calls per physical transaction.
   - An anti-contamination guard verifies that if the active physical transaction was already bound to a different tenant, it aborts immediately by throwing `SecurityException`.
   - If session setting fails on a PostgreSQL engine, it aborts the transaction immediately (fail-closed) by throwing a `SecurityException`. Driver metadata failure also assumes PostgreSQL fail-closed.
5. **System Maintenance & Partition Lifecycle:**
   - Cross-tenant maintenance jobs (e.g., `SagaTimeoutService`) execute with transactional cryptographic token bypass `SELECT set_config('app.system_maintenance_token', :token, true)` scoped strictly to the maintenance transaction (`is_local = true`).
   - PostgreSQL validates the token via `verify_system_maintenance_token()` marked `STABLE` against a private `system_maintenance_auth` table inaccessible to `farmflow_app` and seeded with `ON CONFLICT DO NOTHING`.
   - An administrative procedure `rotate_system_maintenance_token` (accessible only to `postgres`) enforces secure token rotation without schema restarts.
   - The application runtime enforces that in production profiles (`prod`), any attempt to start with default, missing, or weak (<32 chars) maintenance tokens triggers an immediate fatal startup error (`IllegalStateException`).
   - Dynamic monthly partition creation runs via the PostgreSQL stored function `create_telemetry_partition` defined with `SECURITY DEFINER`, verifying name regex, chronological start/end boundaries, and month alignment, ensuring partitions are created under migration owner privileges with `FORCE ROW LEVEL SECURITY` without granting DDL permissions or table ownership to `farmflow_app`.

## Consequences
- **Positive:** True defense-in-depth: cross-tenant access is rejected at the database engine kernel level, even if an application SQL query omits the `WHERE farm_id = ?` clause.
- **Positive:** Fail-closed guarantees prevent any record visibility when connection authentication is uninitialized.
- **Positive:** Cross-tenant inserts or mutations violate `WITH CHECK`, aborting the transaction immediately.
- **Positive:** Superuser privileges are completely eliminated from the runtime application connection pool.
- **Positive:** Automated partition creation is sandboxed through `SECURITY DEFINER` procedures without granting DDL permissions to the application role and with boundary poisoning prevention.
- **Positive:** Cryptographic token cannot be spoofed via SQL injection, cannot be reset on re-migration, and cannot operate with weak/default values in production.
