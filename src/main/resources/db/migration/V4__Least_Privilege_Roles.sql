-- ─────────────────────────────────────────────────────────────────────────────
-- Least-Privilege DB Roles: separates DDL owner from runtime DML user.
--
-- WHY two roles instead of one postgres superuser:
--   The application pod should never be able to DROP TABLE, ALTER COLUMN, or
--   execute any DDL — not because we distrust our own code, but because:
--   1. If an attacker exploits an injection vulnerability, they get DML scope only
--      (SELECT/INSERT/UPDATE/DELETE) — they cannot destroy the schema.
--   2. Runaway Flyway on a misconfigured pod cannot accidentally migrate prod data.
--   3. Satisfies SOC2 / PCI-DSS principle of least privilege for database access.
--
-- Role design:
--   ticket_migration_user — owns the schema, runs Flyway migrations (DDL).
--                           Used ONLY by the K8s Job / initContainer at deploy time.
--   ticket_app_user       — runtime role with DML rights only (SELECT/INSERT/UPDATE/DELETE).
--                           Used by the Spring Boot application pods.
--
-- NOTE: In Docker Compose (local dev) we continue to use the 'postgres' superuser
--       for simplicity — a single developer machine has no threat model that warrants
--       role separation. The roles defined here are applied in production environments
--       where the app connects as ticket_app_user (injected via DB_USER env var).
-- ─────────────────────────────────────────────────────────────────────────────

-- Create runtime application role (DML only — no DDL).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ticket_app_user') THEN
        CREATE ROLE ticket_app_user WITH LOGIN PASSWORD 'changeme_in_prod';
    END IF;
END
$$;

-- Grant connection to the database.
GRANT CONNECT ON DATABASE trouble_ticket TO ticket_app_user;

-- Grant DML on existing tables.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE trouble_ticket TO ticket_app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE note TO ticket_app_user;

-- Grant usage on sequences (needed for IDENTITY columns).
GRANT USAGE, SELECT ON SEQUENCE trouble_ticket_seq TO ticket_app_user;
GRANT USAGE, SELECT ON SEQUENCE note_seq TO ticket_app_user;

-- Ensure future tables created by migrations are also accessible.
-- WHY ALTER DEFAULT PRIVILEGES:
--   Without this, every new table added in future Flyway migrations would require
--   an explicit GRANT. Default privileges apply the grant automatically to any
--   table created by the current user (postgres / migration user) in this schema.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ticket_app_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ticket_app_user;
