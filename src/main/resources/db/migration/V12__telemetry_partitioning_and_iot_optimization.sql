CREATE INDEX IF NOT EXISTS idx_telemetry_farm_type_recorded_at
    ON telemetry_readings (farm_id, reading_type, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_sensor_recorded_at
    ON telemetry_readings (sensor_id, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_plantation_recorded_at
    ON telemetry_readings (plantation_id, recorded_at DESC)
    WHERE plantation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_telemetry_recorded_at_brin
    ON telemetry_readings USING brin (recorded_at);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_telemetry_reading_value_physical'
    ) THEN
        ALTER TABLE telemetry_readings
            ADD CONSTRAINT chk_telemetry_reading_value_physical
            CHECK (reading_value >= -100.0 AND reading_value <= 100000.0);
    END IF;
END $$;
