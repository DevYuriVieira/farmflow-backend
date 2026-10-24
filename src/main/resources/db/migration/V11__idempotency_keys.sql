CREATE TABLE IF NOT EXISTS idempotency_keys (
    key VARCHAR(255) PRIMARY KEY,
    status INT NOT NULL,
    content_type VARCHAR(100),
    response_body BYTEA,
    request_hash VARCHAR(64),
    in_progress BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_idempotency_keys_expires_at
    ON idempotency_keys (expires_at);
