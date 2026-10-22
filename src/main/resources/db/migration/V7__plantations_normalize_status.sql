UPDATE plantations SET status = UPPER(status) WHERE status IS NOT NULL;
ALTER TABLE plantations ALTER COLUMN status SET DEFAULT 'PLANTED';
