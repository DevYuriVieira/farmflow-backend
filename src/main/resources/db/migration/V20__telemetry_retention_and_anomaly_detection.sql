CREATE OR REPLACE FUNCTION detach_telemetry_partition(p_name text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
BEGIN
    IF p_name !~ '^telemetry_readings_[0-9]{4}_[0-9]{2}$' THEN
        RAISE EXCEPTION 'Invalid telemetry partition name for detachment: %', p_name;
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'telemetry_readings'::regclass
          AND c.relname = p_name
    ) THEN
        RAISE NOTICE 'Partition % is not currently attached to telemetry_readings', p_name;
        RETURN false;
    END IF;

    EXECUTE format('ALTER TABLE telemetry_readings DETACH PARTITION %I', p_name);
    RETURN true;
END;
$fn$;

REVOKE ALL ON FUNCTION detach_telemetry_partition(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION detach_telemetry_partition(text) TO farmflow_app;

CREATE OR REPLACE FUNCTION get_telemetry_default_partition_row_count()
RETURNS bigint
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $fn$
DECLARE
    v_count bigint := 0;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'telemetry_readings_default') THEN
        EXECUTE 'SELECT COUNT(*) FROM telemetry_readings_default' INTO v_count;
    END IF;
    RETURN v_count;
END;
$fn$;

REVOKE ALL ON FUNCTION get_telemetry_default_partition_row_count() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION get_telemetry_default_partition_row_count() TO farmflow_app;
