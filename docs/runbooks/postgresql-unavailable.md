# Runbook: PostgreSQL Unavailable or Connection Pool Exhausted

## 1. Trigger & Detection
- Alert: `/health/ready` returns HTTP 503 Service Unavailable.
- Symptom: API error rate spikes; logs show `HikariPool-1 - Connection is not available`.

## 2. Immediate Diagnostic Steps
1. Verify if PostgreSQL container or service is running:
   ```bash
   docker ps | grep farmflow-postgres
   pg_isready -h localhost -p 5433 -U postgres
   ```
2. Inspect PostgreSQL active connections and locks:
   ```sql
   SELECT count(*), state FROM pg_stat_activity GROUP BY state;
   ```
3. Inspect disk space and WAL logs:
   ```bash
   df -h /var/lib/postgresql/data
   ```

## 3. Remediation Actions
1. If PostgreSQL crashed, restart the container:
   ```bash
   docker compose restart postgres
   ```
2. If connection pool is saturated by long-running transactions, terminate idle-in-transaction sessions:
   ```sql
   SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
   WHERE state = 'idle in transaction' AND state_change < current_timestamp - INTERVAL '5 minutes';
   ```
3. Verify recovery via `/health/ready` responding `200 OK`.