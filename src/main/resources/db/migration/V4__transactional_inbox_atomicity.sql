ALTER TABLE processed_messages ADD COLUMN IF NOT EXISTS consumer_name VARCHAR(100) DEFAULT 'default' NOT NULL;

ALTER TABLE processed_messages DROP CONSTRAINT IF EXISTS processed_messages_pkey;
ALTER TABLE processed_messages ADD CONSTRAINT pk_processed_messages PRIMARY KEY (consumer_name, message_id);

CREATE INDEX IF NOT EXISTS ix_processed_messages_expurgate 
ON processed_messages (processed_at);
