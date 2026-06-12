-- Enable Row Level Security on trouble_ticket table
ALTER TABLE trouble_ticket ENABLE ROW LEVEL SECURITY;

-- Enable RLS on note table
ALTER TABLE note ENABLE ROW LEVEL SECURITY;

-- Create policy: each tenant can only see their own tickets
-- Policy uses current_setting('app.current_tenant') which will be set per transaction
CREATE POLICY trouble_ticket_tenant_isolation ON trouble_ticket
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- Create policy for notes (inherited through ticket)
CREATE POLICY note_tenant_isolation ON note
    USING (
        trouble_ticket_id IN (
            SELECT id FROM trouble_ticket WHERE tenant_id = current_setting('app.current_tenant', true)
        )
    )
    WITH CHECK (
        trouble_ticket_id IN (
            SELECT id FROM trouble_ticket WHERE tenant_id = current_setting('app.current_tenant', true)
        )
    );

-- Create index for faster current_setting lookups
CREATE INDEX idx_trouble_ticket_tenant_rls ON trouble_ticket(tenant_id)
    WHERE tenant_id IS NOT NULL;

