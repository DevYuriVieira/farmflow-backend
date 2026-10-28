CREATE TABLE IF NOT EXISTS saga_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    farm_id VARCHAR(36) NOT NULL,
    activity_id VARCHAR(36) NOT NULL,
    saga_type VARCHAR(100) NOT NULL,
    transition_from VARCHAR(50) NOT NULL,
    transition_to VARCHAR(50) NOT NULL,
    reason VARCHAR(1000),
    correlation_id VARCHAR(100),
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_saga_audit_farm_activity ON saga_audit_logs(farm_id, activity_id);

ALTER TABLE saga_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE saga_audit_logs FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS saga_audit_tenant_isolation ON saga_audit_logs;
CREATE POLICY saga_audit_tenant_isolation ON saga_audit_logs
    FOR ALL
    USING (
        farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
        OR current_setting('app.system_bypass_rls', true) = 'true'
    )
    WITH CHECK (
        farm_id = NULLIF(current_setting('app.current_farm_id', true), '')
        OR current_setting('app.system_bypass_rls', true) = 'true'
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON saga_audit_logs TO farmflow_app;
