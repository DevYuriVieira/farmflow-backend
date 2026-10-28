-- ==============================================================================
-- FarmFlow Enterprise Database Security - System Maintenance Token Rotation
-- Usage with psql:
--   psql -U postgres -d farmflow -v new_token='your-production-secret-token-min-32-chars' -f rotate-maintenance-token.sql
-- ==============================================================================
\set ON_ERROR_STOP on

BEGIN;

SELECT rotate_system_maintenance_token(:'new_token');

-- Verification
DO $$
DECLARE
    v_accepted boolean;
    v_default_accepted boolean;
BEGIN
    SELECT verify_system_maintenance_token(:'new_token') INTO v_accepted;
    IF NOT v_accepted THEN
        RAISE EXCEPTION 'CRITICAL: Verification of new token failed immediately after rotation!';
    END IF;

    SELECT verify_system_maintenance_token('farmflow-internal-system-maintenance-token-v1') INTO v_default_accepted;
    IF v_default_accepted THEN
        RAISE EXCEPTION 'CRITICAL: Default development token is still accepted after rotation!';
    END IF;

    RAISE NOTICE 'SUCCESS: Maintenance token rotation verified. Default token rejected.';
END $$;

COMMIT;
