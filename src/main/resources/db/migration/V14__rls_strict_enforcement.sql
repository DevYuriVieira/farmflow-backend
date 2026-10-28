DO $$
BEGIN
    -- Drop old permissive / fail-open policies from V13
    DROP POLICY IF EXISTS farms_tenant_isolation ON farms;
    DROP POLICY IF EXISTS plantations_tenant_isolation ON plantations;
    DROP POLICY IF EXISTS activities_tenant_isolation ON activities;
    DROP POLICY IF EXISTS inventory_tenant_isolation ON inventory_items;
    DROP POLICY IF EXISTS machinery_tenant_isolation ON machinery;
    DROP POLICY IF EXISTS pest_records_tenant_isolation ON pest_records;
    DROP POLICY IF EXISTS telemetry_tenant_isolation ON telemetry_readings;

    -- Force RLS even for table owners to avoid accidental bypass
    ALTER TABLE farms ENABLE ROW LEVEL SECURITY;
    ALTER TABLE farms FORCE ROW LEVEL SECURITY;

    ALTER TABLE plantations ENABLE ROW LEVEL SECURITY;
    ALTER TABLE plantations FORCE ROW LEVEL SECURITY;

    ALTER TABLE activities ENABLE ROW LEVEL SECURITY;
    ALTER TABLE activities FORCE ROW LEVEL SECURITY;

    ALTER TABLE inventory_items ENABLE ROW LEVEL SECURITY;
    ALTER TABLE inventory_items FORCE ROW LEVEL SECURITY;

    ALTER TABLE machinery ENABLE ROW LEVEL SECURITY;
    ALTER TABLE machinery FORCE ROW LEVEL SECURITY;

    ALTER TABLE pest_records ENABLE ROW LEVEL SECURITY;
    ALTER TABLE pest_records FORCE ROW LEVEL SECURITY;

    ALTER TABLE telemetry_readings ENABLE ROW LEVEL SECURITY;
    ALTER TABLE telemetry_readings FORCE ROW LEVEL SECURITY;

    -- 1. Farms: isolated by primary key id matching app.current_farm_id (fail-closed)
    CREATE POLICY farms_tenant_isolation ON farms
        FOR ALL
        USING (id = NULLIF(current_setting('app.current_farm_id', true), ''))
        WITH CHECK (id = NULLIF(current_setting('app.current_farm_id', true), ''));

    -- 2. Plantations: isolated by farm_id
    CREATE POLICY plantations_tenant_isolation ON plantations
        FOR ALL
        USING (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''))
        WITH CHECK (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''));

    -- 3. Activities: isolated by farm_id (or system bypass for background saga compensation)
    CREATE POLICY activities_tenant_isolation ON activities
        FOR ALL
        USING (
            farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
            OR current_setting('app.system_bypass_rls', true) = 'true'
        )
        WITH CHECK (
            farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
            OR current_setting('app.system_bypass_rls', true) = 'true'
        );

    -- 4. Inventory Items: isolated by farm_id
    CREATE POLICY inventory_tenant_isolation ON inventory_items
        FOR ALL
        USING (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''))
        WITH CHECK (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''));

    -- 5. Machinery: isolated by farm_id
    CREATE POLICY machinery_tenant_isolation ON machinery
        FOR ALL
        USING (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''))
        WITH CHECK (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''));

    -- 6. Pest Records: isolated by farm_id
    CREATE POLICY pest_records_tenant_isolation ON pest_records
        FOR ALL
        USING (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''))
        WITH CHECK (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''));

    -- 7. Telemetry Readings: isolated by farm_id
    CREATE POLICY telemetry_tenant_isolation ON telemetry_readings
        FOR ALL
        USING (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''))
        WITH CHECK (farm_id = NULLIF(current_setting('app.current_farm_id', true), ''));

    -- 8. Least-privilege application role (NOSUPERUSER, NOBYPASSRLS)
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'farmflow_app') THEN
        CREATE ROLE farmflow_app WITH LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'farmflow';
    END IF;

    GRANT USAGE ON SCHEMA public TO farmflow_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO farmflow_app;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO farmflow_app;

    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO farmflow_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO farmflow_app;

END $$;
