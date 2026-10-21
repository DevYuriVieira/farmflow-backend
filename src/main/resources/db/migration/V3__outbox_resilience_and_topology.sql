-- V3: Schema expansion for Transactional Outbox resilience, status-based claim, and routing metadata
ALTER TABLE outbox_messages ADD COLUMN IF NOT EXISTS routing_key VARCHAR(255);
ALTER TABLE outbox_messages ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255);
ALTER TABLE outbox_messages ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_messages ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING' NOT NULL;

-- Backfill legacy records
UPDATE outbox_messages SET status = 'PUBLISHED' WHERE processed_on IS NOT NULL;
UPDATE outbox_messages SET status = 'DEAD' WHERE processed_on IS NULL AND attempts >= 3;
UPDATE outbox_messages SET routing_key = 'events.general' WHERE routing_key IS NULL;

-- Drop obsolete partial index and create optimized composite indexes
DROP INDEX IF EXISTS ix_outbox_unprocessed;
CREATE INDEX IF NOT EXISTS ix_outbox_unprocessed
ON outbox_messages (occurred_on)
WHERE processed_on IS NULL;

CREATE INDEX IF NOT EXISTS ix_outbox_pending_lease
ON outbox_messages (status, next_attempt_at, occurred_on)
WHERE status IN ('PENDING', 'PROCESSING');
