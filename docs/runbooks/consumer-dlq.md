# Runbook: Consumer Dead-Letter Queue (DLQ) Triage and Reprocessing

## 1. Trigger & Detection
- Alert: Queue depth in `farmflow.events.dlq` > 0.
- Symptom: Log records `Poison message detected in queue... Routing to DLQ`.

## 2. Inspection & Diagnosis
1. Access RabbitMQ Management UI (`http://localhost:15672`).
2. Navigate to `Queues` &rarr; `farmflow.events.dlq`.
3. Click `Get Message(s)` to inspect headers and payload:
   - Check `x-dlq-reason` header (e.g., `MALFORMED_JSON_PAYLOAD`).
   - Check `x-exception-message`.

## 3. Reprocessing & Resolution
1. If the message was rejected due to transient downstream data migration:
   - Resolve downstream bug.
   - Replay message back to `farmflow.events` exchange with the original routing key.
2. If the message is completely unrecoverable or malicious test data:
   - Acknowledge and purge from DLQ after recording audit evidence.