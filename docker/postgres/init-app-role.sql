-- Initialize least-privilege application role (NOSUPERUSER, NOBYPASSRLS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'farmflow_app') THEN
        CREATE ROLE farmflow_app WITH LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'farmflow';
    END IF;
    GRANT USAGE ON SCHEMA public TO farmflow_app;
END $$;
