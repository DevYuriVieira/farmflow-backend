DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'activities' AND column_name = 'status'
    ) THEN
        ALTER TABLE activities ADD COLUMN status VARCHAR(50) DEFAULT 'IN_PROGRESS' NOT NULL;
    END IF;

    -- Normalize existing completed rows
    UPDATE activities SET status = 'COMPLETED' WHERE completed = true AND status != 'COMPLETED';

    -- Add index for state machine polling and queries
    CREATE INDEX IF NOT EXISTS idx_activities_farm_status ON activities(farm_id, status);
END $$;
