-- V21: Enterprise Security Hardening & Audit Trail (PostgreSQL 17/18)
-- 1. Revoke direct DML/access privileges on child partitions of telemetry_readings from PUBLIC and farmflow_app.
--    This guarantees that queries MUST traverse the root partitioned table where FORCE RLS is strictly enforced.
-- 2. Harden create_telemetry_partition to automatically revoke direct access on newly provisioned partitions.
-- 3. Add archive_and_drop_telemetry_partition for controlled post-detachment retention/purging.
-- 4. Create private audit table system_maintenance_audit_trail and context management procedures.

-- 1. Revoke direct access on all existing child partitions of telemetry_readings
DO $$
DECLARE
    r RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'telemetry_readings') THEN
        FOR r IN (
            SELECT inhrelid::regclass AS partition_name
            FROM pg_inherits
            WHERE inhparent = 'telemetry_readings'::regclass
        ) LOOP
            EXECUTE 'REVOKE ALL ON ' || r.partition_name || ' FROM PUBLIC';
            EXECUTE 'REVOKE ALL ON ' || r.partition_name || ' FROM farmflow_app';
        END LOOP;
    END IF;
END $$;

-- 2. Harden create_telemetry_partition to enforce least privilege on child partitions
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
    IF (p_to AT TIME ZONE 'UTC') != ((p_from AT TIME ZONE 'UTC') + INTERVAL '1 month') THEN
        RAISE EXCEPTION 'Partition range must span exactly one calendar month in UTC: from % to %', p_from, p_to;
    END IF;

    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF telemetry_readings FOR VALUES FROM (%L) TO (%L)',
                   p_name, p_from, p_to);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', p_name);
    -- Enforce least privilege: direct access forbidden; access must route through partitioned table
    EXECUTE format('REVOKE ALL ON %I FROM PUBLIC', p_name);
    EXECUTE format('REVOKE ALL ON %I FROM farmflow_app', p_name);
    RETURN true;
END
$fn$;

REVOKE ALL ON FUNCTION create_telemetry_partition(text, timestamptz, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION create_telemetry_partition(text, timestamptz, timestamptz) TO farmflow_app;

-- 3. Procedure for post-detach archival and purge
CREATE OR REPLACE FUNCTION archive_and_drop_telemetry_partition(p_name text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
BEGIN
    IF p_name !~ '^telemetry_readings_[0-9]{4}_[0-9]{2}$' THEN
        RAISE EXCEPTION 'Invalid telemetry partition name for archival/purging: %', p_name;
    END IF;

    -- Detach partition first if currently attached
    IF EXISTS (
        SELECT 1
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'telemetry_readings'::regclass
          AND c.relname = p_name
    ) THEN
        EXECUTE format('ALTER TABLE telemetry_readings DETACH PARTITION %I', p_name);
    END IF;

    -- Drop detached historical partition (in production, preceded by cold-storage archival)
    EXECUTE format('DROP TABLE IF EXISTS %I', p_name);
    RETURN true;
END;
$fn$;

REVOKE ALL ON FUNCTION archive_and_drop_telemetry_partition(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION archive_and_drop_telemetry_partition(text) TO farmflow_app;

-- 4. Private maintenance audit trail
CREATE TABLE IF NOT EXISTS system_maintenance_audit_trail (
    id BIGSERIAL PRIMARY KEY,
    client_addr inet,
    client_port integer,
    authenticated_user text NOT NULL,
    action text NOT NULL,
    success boolean NOT NULL,
    event_timestamp timestamptz NOT NULL DEFAULT now()
);

REVOKE ALL ON system_maintenance_audit_trail FROM PUBLIC;
REVOKE ALL ON system_maintenance_audit_trail FROM farmflow_app;

-- 5. Audited maintenance context elevation procedure
CREATE OR REPLACE FUNCTION enable_system_maintenance_context(p_token text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
DECLARE
    v_expected text;
    v_valid boolean := false;
BEGIN
    IF p_token IS NOT NULL AND length(p_token) >= 16 THEN
        SELECT token_hash INTO v_expected FROM system_maintenance_auth WHERE id = 1;
        IF v_expected IS NOT NULL AND encode(sha256(p_token::bytea), 'hex') = v_expected THEN
            v_valid := true;
        END IF;
    END IF;

    -- Record audit log entry
    INSERT INTO system_maintenance_audit_trail (client_addr, client_port, authenticated_user, action, success, event_timestamp)
    VALUES (inet_client_addr(), inet_client_port(), session_user, 'ELEVATE_MAINTENANCE_CONTEXT', v_valid, now());

    IF v_valid THEN
        PERFORM set_config('app.system_maintenance_token', p_token, true);
        RETURN true;
    ELSE
        PERFORM set_config('app.system_maintenance_token', '', true);
        RETURN false;
    END IF;
EXCEPTION WHEN OTHERS THEN
    INSERT INTO system_maintenance_audit_trail (client_addr, client_port, authenticated_user, action, success, event_timestamp)
    VALUES (inet_client_addr(), inet_client_port(), session_user, 'ELEVATE_MAINTENANCE_CONTEXT_ERROR', false, now());
    PERFORM set_config('app.system_maintenance_token', '', true);
    RETURN false;
END;
$fn$;

REVOKE ALL ON FUNCTION enable_system_maintenance_context(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION enable_system_maintenance_context(text) TO farmflow_app;

-- 6. Audited maintenance context sanitization procedure
CREATE OR REPLACE FUNCTION disable_system_maintenance_context()
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
BEGIN
    PERFORM set_config('app.system_maintenance_token', '', true);
    INSERT INTO system_maintenance_audit_trail (client_addr, client_port, authenticated_user, action, success, event_timestamp)
    VALUES (inet_client_addr(), inet_client_port(), session_user, 'CLEAR_MAINTENANCE_CONTEXT', true, now());
    RETURN true;
EXCEPTION WHEN OTHERS THEN
    PERFORM set_config('app.system_maintenance_token', '', true);
    RETURN true;
END;
$fn$;

REVOKE ALL ON FUNCTION disable_system_maintenance_context() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION disable_system_maintenance_context() TO farmflow_app;
