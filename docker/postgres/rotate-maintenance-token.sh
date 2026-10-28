#!/usr/bin/env bash
# ==============================================================================
# FarmFlow Enterprise Database Security - System Maintenance Token Rotation
# Must be executed by database administrator (postgres / superuser)
# ==============================================================================
set -euo pipefail

NEW_TOKEN="${1:-${FARMFLOW_SYSTEM_MAINTENANCE_TOKEN:-}}"
DB_HOST="${DB_HOST:-${POSTGRES_HOST:-localhost}}"
DB_PORT="${DB_PORT:-${POSTGRES_PORT:-5432}}"
DB_NAME="${DB_NAME:-${POSTGRES_DB:-farmflow}}"
DB_USER="${DB_USER:-${POSTGRES_USER:-postgres}}"

if [ -z "$NEW_TOKEN" ]; then
    echo "ERROR: Missing maintenance token. Provide as argument or via FARMFLOW_SYSTEM_MAINTENANCE_TOKEN env var." >&2
    echo "Usage: $0 <new-token-min-32-chars>" >&2
    exit 1
fi

if [ "${#NEW_TOKEN}" -lt 32 ]; then
    echo "ERROR: Maintenance token must be at least 32 characters (received ${#NEW_TOKEN})." >&2
    exit 1
fi

if [ "$NEW_TOKEN" = "farmflow-internal-system-maintenance-token-v1" ]; then
    echo "ERROR: Cannot rotate to default development token." >&2
    exit 1
fi

echo "Connecting to PostgreSQL at ${DB_HOST}:${DB_PORT}/${DB_NAME} as ${DB_USER}..."

# Execute rotation within transaction
RESULT=$(PGPASSWORD="${POSTGRES_PASSWORD:-postgres}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -A -v ON_ERROR_STOP=1 -c "
BEGIN;
SELECT rotate_system_maintenance_token('$NEW_TOKEN');
COMMIT;
")

if [ "$RESULT" != "t" ]; then
    echo "ERROR: rotate_system_maintenance_token returned '$RESULT' instead of 't'." >&2
    exit 1
fi

# Verification step
VERIFY_NEW=$(PGPASSWORD="${POSTGRES_PASSWORD:-postgres}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -A -c "
SELECT verify_system_maintenance_token('$NEW_TOKEN');
")

VERIFY_OLD=$(PGPASSWORD="${POSTGRES_PASSWORD:-postgres}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -A -c "
SELECT verify_system_maintenance_token('farmflow-internal-system-maintenance-token-v1');
")

if [ "$VERIFY_NEW" = "t" ] && [ "$VERIFY_OLD" = "f" ]; then
    echo "SUCCESS: Maintenance token successfully rotated in database!"
    echo " - Active hash updated for configured token."
    echo " - Default dev token is permanently rejected."
    exit 0
else
    echo "ERROR: Verification failed (new_valid=$VERIFY_NEW, old_valid=$VERIFY_OLD)" >&2
    exit 1
fi
