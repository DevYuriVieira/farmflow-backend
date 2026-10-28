-- V18: Enterprise Security Hardening (PostgreSQL)
-- 1. Revoke table ownership of telemetry_readings and all partitions from application role.
-- 2. Expose partition creation through a SECURITY DEFINER function with strict regex and FORCE RLS.
-- 3. Replace loose app.system_bypass_rls with a cryptographic token verification function and private auth table.

DO $$
DECLARE
    r RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'telemetry_readings') THEN
        EXECUTE 'ALTER TABLE telemetry_readings OWNER TO ' || quote_ident(current_user);
        EXECUTE 'ALTER TABLE telemetry_readings FORCE ROW LEVEL SECURITY';

        -- Transfer ownership and enforce FORCE RLS on all existing child partitions
        FOR r IN (
            SELECT inhrelid::regclass AS partition_name
            FROM pg_inherits
            WHERE inhparent = 'telemetry_readings'::regclass
        ) LOOP
            EXECUTE 'ALTER TABLE ' || r.partition_name || ' OWNER TO ' || quote_ident(current_user);
            EXECUTE 'ALTER TABLE ' || r.partition_name || ' FORCE ROW LEVEL SECURITY';
        END LOOP;
    END IF;
END $$;

-- 2. SECURITY DEFINER function for dynamic monthly partitions
CREATE OR REPLACE FUNCTION create_telemetry_partition(p_name text, p_from timestamptz, p_to timestamptz)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
BEGIN
    IF p_name !~ '^telemetry_readings_[0-9]{4}_[0-9]{2}$' THEN
        RAISE EXCEPTION 'Invalid partition name: %', p_name;
    END IF;
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF telemetry_readings FOR VALUES FROM (%L) TO (%L)',
                   p_name, p_from, p_to);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', p_name);
    RETURN true;
END
$fn$;

REVOKE ALL ON FUNCTION create_telemetry_partition(text, timestamptz, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION create_telemetry_partition(text, timestamptz, timestamptz) TO farmflow_app;

-- 3. Private maintenance auth table (Inaccessible to farmflow_app)
CREATE TABLE IF NOT EXISTS system_maintenance_auth (
    id int PRIMARY KEY,
    token_hash text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Deny all access to application role and public
REVOKE ALL ON system_maintenance_auth FROM PUBLIC;
REVOKE ALL ON system_maintenance_auth FROM farmflow_app;

-- Seed default maintenance token hash (SHA-256 of 'farmflow-internal-system-maintenance-token-v1')
INSERT INTO system_maintenance_auth (id, token_hash, updated_at)
VALUES (1, encode(sha256('farmflow-internal-system-maintenance-token-v1'::bytea), 'hex'), now())
ON CONFLICT (id) DO UPDATE SET token_hash = EXCLUDED.token_hash, updated_at = now();

-- 4. Cryptographic token verification function
CREATE OR REPLACE FUNCTION verify_system_maintenance_token(p_token text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
DECLARE
    v_expected text;
BEGIN
    IF p_token IS NULL OR length(p_token) < 16 THEN
        RETURN false;
    END IF;
    SELECT token_hash INTO v_expected FROM system_maintenance_auth WHERE id = 1;
    IF v_expected IS NULL THEN
        RETURN false;
    END IF;
    RETURN encode(sha256(p_token::bytea), 'hex') = v_expected;
EXCEPTION WHEN OTHERS THEN
    RETURN false;
END;
$fn$;

REVOKE ALL ON FUNCTION verify_system_maintenance_token(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION verify_system_maintenance_token(text) TO farmflow_app;

-- 5. Hardened RLS policies replacing loose app.system_bypass_rls with verify_system_maintenance_token
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'activities') THEN
        DROP POLICY IF EXISTS activities_tenant_isolation ON activities;
        CREATE POLICY activities_tenant_isolation ON activities
            FOR ALL
            USING (
                farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
                OR verify_system_maintenance_token(current_setting('app.system_maintenance_token', true)) = true
            )
            WITH CHECK (
                farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
                OR verify_system_maintenance_token(current_setting('app.system_maintenance_token', true)) = true
            );
    END IF;

    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'saga_audit_logs') THEN
        DROP POLICY IF EXISTS saga_audit_logs_tenant_isolation ON saga_audit_logs;
        CREATE POLICY saga_audit_logs_tenant_isolation ON saga_audit_logs
            FOR ALL
            USING (
                farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
                OR verify_system_maintenance_token(current_setting('app.system_maintenance_token', true)) = true
            )
            WITH CHECK (
                farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
                OR verify_system_maintenance_token(current_setting('app.system_maintenance_token', true)) = true
            );
    END IF;
END $$;
