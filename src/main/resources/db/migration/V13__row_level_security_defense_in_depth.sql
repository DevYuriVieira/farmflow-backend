DO $$
BEGIN
    ALTER TABLE farms ENABLE ROW LEVEL SECURITY;
    ALTER TABLE plantations ENABLE ROW LEVEL SECURITY;
    ALTER TABLE activities ENABLE ROW LEVEL SECURITY;
    ALTER TABLE inventory_items ENABLE ROW LEVEL SECURITY;
    ALTER TABLE machinery ENABLE ROW LEVEL SECURITY;
    ALTER TABLE pest_records ENABLE ROW LEVEL SECURITY;
    ALTER TABLE telemetry_readings ENABLE ROW LEVEL SECURITY;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'farms_tenant_isolation' AND tablename = 'farms'
    ) THEN
        CREATE POLICY farms_tenant_isolation ON farms
            FOR ALL
            USING (
                NULLIF(current_setting('app.current_farm_id', true), '') IS NULL
                OR current_setting('app.current_farm_id', true) = 'BYPASS'
                OR id = current_setting('app.current_farm_id', true)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'plantations_tenant_isolation' AND tablename = 'plantations'
    ) THEN
        CREATE POLICY plantations_tenant_isolation ON plantations
            FOR ALL
            USING (
                NULLIF(current_setting('app.current_farm_id', true), '') IS NULL
                OR current_setting('app.current_farm_id', true) = 'BYPASS'
                OR farm_id = current_setting('app.current_farm_id', true)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'activities_tenant_isolation' AND tablename = 'activities'
    ) THEN
        CREATE POLICY activities_tenant_isolation ON activities
            FOR ALL
            USING (
                NULLIF(current_setting('app.current_farm_id', true), '') IS NULL
                OR current_setting('app.current_farm_id', true) = 'BYPASS'
                OR farm_id = current_setting('app.current_farm_id', true)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'inventory_tenant_isolation' AND tablename = 'inventory_items'
    ) THEN
        CREATE POLICY inventory_tenant_isolation ON inventory_items
            FOR ALL
            USING (
                NULLIF(current_setting('app.current_farm_id', true), '') IS NULL
                OR current_setting('app.current_farm_id', true) = 'BYPASS'
                OR farm_id = current_setting('app.current_farm_id', true)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'machinery_tenant_isolation' AND tablename = 'machinery'
    ) THEN
        CREATE POLICY machinery_tenant_isolation ON machinery
            FOR ALL
            USING (
                NULLIF(current_setting('app.current_farm_id', true), '') IS NULL
                OR current_setting('app.current_farm_id', true) = 'BYPASS'
                OR farm_id = current_setting('app.current_farm_id', true)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'pest_records_tenant_isolation' AND tablename = 'pest_records'
    ) THEN
        CREATE POLICY pest_records_tenant_isolation ON pest_records
            FOR ALL
            USING (
                NULLIF(current_setting('app.current_farm_id', true), '') IS NULL
                OR current_setting('app.current_farm_id', true) = 'BYPASS'
                OR farm_id = current_setting('app.current_farm_id', true)
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'telemetry_tenant_isolation' AND tablename = 'telemetry_readings'
    ) THEN
        CREATE POLICY telemetry_tenant_isolation ON telemetry_readings
            FOR ALL
            USING (
                NULLIF(current_setting('app.current_farm_id', true), '') IS NULL
                OR current_setting('app.current_farm_id', true) = 'BYPASS'
                OR farm_id = current_setting('app.current_farm_id', true)
            );
    END IF;
END $$;
