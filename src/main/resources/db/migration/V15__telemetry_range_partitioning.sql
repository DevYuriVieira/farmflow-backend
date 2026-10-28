DO $$
BEGIN
    -- 1. Create the partitioned table by range of recorded_at
    CREATE TABLE IF NOT EXISTS telemetry_readings_partitioned (
        id VARCHAR(36) NOT NULL,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
        version BIGINT DEFAULT 0,
        farm_id VARCHAR(36) NOT NULL,
        plantation_id VARCHAR(36),
        sensor_id VARCHAR(100) NOT NULL,
        reading_type VARCHAR(50) NOT NULL,
        reading_value DOUBLE PRECISION NOT NULL,
        unit_measure VARCHAR(50) NOT NULL,
        battery_level VARCHAR(50),
        recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
        CONSTRAINT chk_telemetry_reading_value_physical CHECK (reading_value >= -100.0 AND reading_value <= 100000.0),
        PRIMARY KEY (id, recorded_at)
    ) PARTITION BY RANGE (recorded_at);

    -- 2. Create monthly partitions
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_01 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2026-02-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_02 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-02-01 00:00:00+00') TO ('2026-03-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_03 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-03-01 00:00:00+00') TO ('2026-04-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_04 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-04-01 00:00:00+00') TO ('2026-05-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_05 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_06 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_07 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_08 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_09 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_10 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_11 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2026_12 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');
    CREATE TABLE IF NOT EXISTS telemetry_readings_2027_01 PARTITION OF telemetry_readings_partitioned
        FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00');

    -- Default fallback partition
    CREATE TABLE IF NOT EXISTS telemetry_readings_default PARTITION OF telemetry_readings_partitioned DEFAULT;

    -- 3. Copy existing data if telemetry_readings exists
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'telemetry_readings'
    ) THEN
        INSERT INTO telemetry_readings_partitioned (
            id, created_at, updated_at, version, farm_id, plantation_id,
            sensor_id, reading_type, reading_value, unit_measure, battery_level,
            recorded_at
        )
        SELECT
            id, created_at, updated_at, COALESCE(version, 0), farm_id, plantation_id,
            sensor_id, reading_type, reading_value, unit_measure, battery_level,
            COALESCE(recorded_at, created_at, NOW())
        FROM telemetry_readings
        ON CONFLICT DO NOTHING;

        DROP TABLE telemetry_readings CASCADE;
    END IF;

    -- 4. Rename partitioned table to official table name
    ALTER TABLE telemetry_readings_partitioned RENAME TO telemetry_readings;

    -- 5. Recreate optimized indexes (BRIN for time series range scans)
    CREATE INDEX IF NOT EXISTS idx_telemetry_recorded_at_brin
        ON telemetry_readings USING brin (recorded_at);

    CREATE INDEX IF NOT EXISTS idx_telemetry_farm_type_recorded_at
        ON telemetry_readings (farm_id, reading_type, recorded_at DESC);

    CREATE INDEX IF NOT EXISTS idx_telemetry_sensor_recorded_at
        ON telemetry_readings (sensor_id, recorded_at DESC);

    -- 6. Enforce strict Row Level Security (RLS) on partitioned table and partitions
    ALTER TABLE telemetry_readings ENABLE ROW LEVEL SECURITY;
    ALTER TABLE telemetry_readings FORCE ROW LEVEL SECURITY;

    DROP POLICY IF EXISTS telemetry_tenant_isolation ON telemetry_readings;
    CREATE POLICY telemetry_tenant_isolation ON telemetry_readings
        FOR ALL
        USING (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''))
        WITH CHECK (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''));

    -- 7. Grant DML to farmflow_app while maintaining table ownership under postgres (Flyway migration user).
    -- Table partition creation is strictly delegated to SECURITY DEFINER functions (create_telemetry_partition)
    -- to prevent application role from possessing DDL or ALTER TABLE privileges.
    GRANT SELECT, INSERT, UPDATE, DELETE ON telemetry_readings TO farmflow_app;

END $$;
