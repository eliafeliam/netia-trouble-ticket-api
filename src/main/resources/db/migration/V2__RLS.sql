-- ─────────────────────────────────────────────────────────────────────────────
-- Row Level Security (RLS): defense-in-depth tenant isolation enforced by Postgres
-- itself, not by application WHERE-clauses.
--
-- How it works: the app opens a transaction and runs "SET LOCAL app.current_tenant = '<id>'"
-- (done automatically by TenantStatementInspector). Postgres then transparently appends the
-- policy predicate to every query against these tables. Even if a developer forgets a tenant
-- filter (or writes a raw query), the engine still refuses to return another tenant's rows.
-- This is the "железобетонный" guarantee auditors want for a Tier-1 / GDPR system.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE trouble_ticket ENABLE ROW LEVEL SECURITY;
ALTER TABLE note ENABLE ROW LEVEL SECURITY;

-- CRITICAL: ENABLE alone is bypassed for the table OWNER and for SUPERUSERS. FORCE makes the
-- policies apply to the owner too. Superusers still bypass RLS entirely, so in production the
-- application MUST connect with a dedicated NON-superuser, NON-owner role (e.g. ticket_app) —
-- otherwise this isolation is silently inert. (docker-compose uses 'postgres' for convenience
-- only; do not do that in prod.)
ALTER TABLE trouble_ticket FORCE ROW LEVEL SECURITY;
ALTER TABLE note FORCE ROW LEVEL SECURITY;

-- Tenant can only see/write its own tickets.
-- current_setting('app.current_tenant', true): the 'true' = missing_ok, so a session that
-- never set the GUC gets NULL (and the predicate is false) instead of an error.
CREATE POLICY trouble_ticket_tenant_isolation ON trouble_ticket
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- Notes inherit tenant scope through their parent ticket.
-- NOTE: the FK column is note.trouble_ticket_pk -> trouble_ticket.pk (the internal BIGINT key),
-- NOT the external VARCHAR "id". Earlier this policy referenced non-existent columns and the
-- migration failed on startup; this is the corrected version.
CREATE POLICY note_tenant_isolation ON note
    USING (
        trouble_ticket_pk IN (
            SELECT pk FROM trouble_ticket
            WHERE tenant_id = current_setting('app.current_tenant', true)
        )
    )
    WITH CHECK (
        trouble_ticket_pk IN (
            SELECT pk FROM trouble_ticket
            WHERE tenant_id = current_setting('app.current_tenant', true)
        )
    );

-- Supports the RLS predicate lookups on tenant_id.
CREATE INDEX idx_trouble_ticket_tenant_rls ON trouble_ticket(tenant_id)
    WHERE tenant_id IS NOT NULL;
