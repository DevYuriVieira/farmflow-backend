# Runbook: Outbox Message Backlog Accumulation

## 1. Trigger & Detection
- Alert: Pending outbox messages exceed threshold (`countByProcessedOnIsNull() > 500`).
- Symptom: Telemetry notifications or pest activity alerts delayed by > 60 seconds.

## 2. Root Cause Analysis
1. Check broker connectivity and network latency.
2. Query pending attempts and errors in the outbox table:
   ```sql
   SELECT id, event_type, attempts, last_error, occurred_on 
   FROM outbox_messages 
   WHERE processed_on IS NULL 
   ORDER BY attempts DESC LIMIT 20;
   ```

## 3. Mitigation Actions
1. If messages have reached `attempts >= 3` due to a downstream schema mismatch:
   - Identify the invalid payload.
   - Fix downstream consumer or patch payload.
   - Reset attempts to allow re-processing:
     ```sql
     UPDATE outbox_messages SET attempts = 0 WHERE processed_on IS NULL AND attempts >= 3;
     ```
2. Scale out API replicas to increase parallel consumer drain throughput (`FOR UPDATE SKIP LOCKED` ensures zero duplicate locks).