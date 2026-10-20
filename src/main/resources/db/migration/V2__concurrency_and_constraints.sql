CREATE TABLE IF NOT EXISTS outbox_messages (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    trace_parent VARCHAR(255),
    occurred_on TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_on TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS ix_outbox_unprocessed 
ON outbox_messages (occurred_on) 
WHERE processed_on IS NULL;

CREATE TABLE IF NOT EXISTS processed_messages (
    message_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_processed_messages_processed_at 
ON processed_messages (processed_at);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_machinery_hours_positive'
    ) THEN
        ALTER TABLE machinery ADD CONSTRAINT chk_machinery_hours_positive CHECK (hours_used >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_qty_positive'
    ) THEN
        ALTER TABLE inventory_items ADD CONSTRAINT chk_inventory_qty_positive CHECK (quantity >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_min_threshold_positive'
    ) THEN
        ALTER TABLE inventory_items ADD CONSTRAINT chk_inventory_min_threshold_positive CHECK (min_threshold >= 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_machinery_active_maintenance 
ON machinery(id) 
WHERE status = 'maintenance';

CREATE INDEX IF NOT EXISTS ix_pest_records_recent 
ON pest_records(plantation_id, pest_name, detected_at);