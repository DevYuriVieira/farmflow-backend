# Runbook: RabbitMQ Broker Unavailable

## 1. Trigger & Detection
- Alert: `/health/dependencies` reports status `DEGRADED` with `rabbitmq: UNHEALTHY`.
- Metric: `outbox_messages` pending count increases.

## 2. Impact Assessment
- **Transactional Writes:** Unaffected! State mutations continue committing to PostgreSQL, and events accumulate safely in `outbox_messages` (zero data loss).
- **Asynchronous Consumers:** Temporarily suspended until broker reconnects.

## 3. Remediation Actions
1. Check broker container status:
   ```bash
   docker compose ps rabbitmq
   docker compose logs --tail 50 rabbitmq
   ```
2. Restart RabbitMQ:
   ```bash
   docker compose restart rabbitmq
   ```
3. Observe Outbox auto-drainage:
   - Monitor `OutboxProcessor` logs.
   - Confirm `/health/dependencies` returns `200 OK HEALTHY`.