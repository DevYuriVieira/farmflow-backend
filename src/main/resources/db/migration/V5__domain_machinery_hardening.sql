ALTER TABLE machinery ADD COLUMN IF NOT EXISTS maintenance_reason VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_machinery_status_valid'
    ) THEN
        ALTER TABLE machinery ADD CONSTRAINT chk_machinery_status_valid 
        CHECK (status IN ('OPERATIONAL', 'MAINTENANCE', 'INACTIVE', 'operational', 'maintenance', 'inactive'));
    END IF;
END $$;
