# Runbook: Enterprise System Maintenance Token Rotation

## 1. Trigger & Overview
- **Trigger**: Production initial rollout, scheduled credential rotation (e.g. 90 days), or suspected secret leakage.
- **Context**: FarmFlow core uses PostgreSQL Row-Level Security (RLS) bypass via `verify_system_maintenance_token` solely for cross-tenant saga timeout compensations and maintenance tasks.
- **Security Invariant**: Production containers fail startup (`IllegalStateException`) if:
  1. The database still accepts the default development token (`farmflow-internal-system-maintenance-token-v1`).
  2. The injected `FARMFLOW_SYSTEM_MAINTENANCE_TOKEN` does not match the active SHA-256 hash in `system_maintenance_auth`.
  3. The token has length $< 32$ characters.

## 2. Pre-Deployment Secret Generation
Generate a cryptographically secure random secret (min 32 characters, base64 or alphanumeric):
```bash
NEW_TOKEN=$(openssl rand -base64 36 | tr -dc 'a-zA-Z0-9_-' | fold -w 40 | head -n 1)
echo "Generated token length: ${#NEW_TOKEN}"
```

## 3. Database Token Rotation Procedure (Administrative Role)
> **Note**: `rotate_system_maintenance_token` has `REVOKE ALL` from `farmflow_app` and `PUBLIC`. It MUST be executed by the `postgres` superuser/admin role.

### Option A: Automated Script
```bash
chmod +x ./docker/postgres/rotate-maintenance-token.sh
FARMFLOW_SYSTEM_MAINTENANCE_TOKEN="$NEW_TOKEN" \
POSTGRES_HOST="db-cluster.internal" \
POSTGRES_PORT=5432 \
POSTGRES_DB="farmflow" \
POSTGRES_USER="postgres" \
POSTGRES_PASSWORD="admin_password" \
./docker/postgres/rotate-maintenance-token.sh
```

### Option B: Direct psql / Helm Hook / Migration Job
```bash
psql -h db-cluster.internal -U postgres -d farmflow \
  -v new_token="$NEW_TOKEN" \
  -f ./docker/postgres/rotate-maintenance-token.sql
```

## 4. Application Configuration & Rollout
1. Store `$NEW_TOKEN` in Secret Manager / Kubernetes Secrets / Vault:
   ```yaml
   apiVersion: v1
   kind: Secret
   metadata:
     name: farmflow-secrets
   stringData:
     FARMFLOW_SYSTEM_MAINTENANCE_TOKEN: "<NEW_TOKEN>"
   ```
2. Trigger rolling restart or blue-green deploy of `farmflow-backend` (or `core-service`).
3. Verify pod/container boot log:
   - Must NOT contain `CRITICAL SECURITY VIOLATION`.
   - Core service health check `/health/ready` returns `HTTP 200`.

## 5. Rollback & Emergency Recovery
If token rotation desynchronizes before containers update:
1. Re-execute `rotate_system_maintenance_token` passing the previous active secret.
2. Confirm with:
   ```sql
   SELECT verify_system_maintenance_token('<PREVIOUS_TOKEN>');
   ```
3. Restart failed services.
