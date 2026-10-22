UPDATE activities SET type = UPPER(type) WHERE type IS NOT NULL;
UPDATE activities SET priority = UPPER(priority) WHERE priority IS NOT NULL;
ALTER TABLE activities ALTER COLUMN priority SET DEFAULT 'MEDIUM';
