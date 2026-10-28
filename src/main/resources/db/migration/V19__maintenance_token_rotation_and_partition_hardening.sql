-- V19: Enterprise Security Hardening (PostgreSQL)
-- 1. Harden partition creation with UTC datetime boundary arithmetic and name matching.
-- 2. Optimize verify_system_maintenance_token as STABLE for optimizer index plan retention.
-- 3. Add administrative rotation procedure rotate_system_maintenance_token.
-- 4. Clean up legacy saga_audit_tenant_isolation policy from V17.

-- 1. Partition creation with strict UTC arithmetic
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
    IF p_from >= p_to THEN
        RAISE EXCEPTION 'Invalid partition range: p_from (%) must be strictly before p_to (%)', p_from, p_to;
    END IF;
    IF ('telemetry_readings_' || to_char(p_from AT TIME ZONE 'UTC', 'YYYY_MM')) != p_name THEN
        RAISE EXCEPTION 'Partition name (%) does not match start timestamp boundary (%)', p_name, p_from;
    END IF;
    -- Validate range strictly using UTC timezone arithmetic to prevent session timezone / DST shift issues
    IF (p_to AT TIME ZONE 'UTC') != ((p_from AT TIME ZONE 'UTC') + INTERVAL '1 month') THEN
        RAISE EXCEPTION 'Partition range must span exactly one calendar month in UTC: from % to %', p_from, p_to;
    END IF;
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF telemetry_readings FOR VALUES FROM (%L) TO (%L)',
                   p_name, p_from, p_to);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', p_name);
    RETURN true;
END
$fn$;

REVOKE ALL ON FUNCTION create_telemetry_partition(text, timestamptz, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION create_telemetry_partition(text, timestamptz, timestamptz) TO farmflow_app;

-- 2. STABLE cryptographic token verification function
CREATE OR REPLACE FUNCTION verify_system_maintenance_token(p_token text)
RETURNS boolean
LANGUAGE plpgsql
STABLE
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

-- 3. Administrative rotation procedure (restricted to superuser/admin)
CREATE OR REPLACE FUNCTION rotate_system_maintenance_token(p_new_token text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
BEGIN
    IF p_new_token IS NULL OR length(trim(p_new_token)) < 32 THEN
        RAISE EXCEPTION 'New maintenance token must be at least 32 characters long';
    END IF;
    IF p_new_token = 'farmflow-internal-system-maintenance-token-v1' THEN
        RAISE EXCEPTION 'Cannot use default development token in rotation';
    END IF;
    UPDATE system_maintenance_auth
    SET token_hash = encode(sha256(p_new_token::bytea), 'hex'), updated_at = now()
    WHERE id = 1;
    IF NOT FOUND THEN
        INSERT INTO system_maintenance_auth (id, token_hash, updated_at)
        VALUES (1, encode(sha256(p_new_token::bytea), 'hex'), now());
    END IF;
    RETURN true;
END;
$fn$;

REVOKE ALL ON FUNCTION rotate_system_maintenance_token(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION rotate_system_maintenance_token(text) FROM farmflow_app;

-- 4. Clean up legacy V17 policy on saga_audit_logs if present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'saga_audit_logs') THEN
        DROP POLICY IF EXISTS saga_audit_tenant_isolation ON saga_audit_logs;
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
