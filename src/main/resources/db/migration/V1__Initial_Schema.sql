CREATE SEQUENCE trouble_ticket_seq START 1000;
CREATE SEQUENCE note_seq START 1000;

CREATE TABLE trouble_ticket (
    pk BIGINT PRIMARY KEY DEFAULT nextval('trouble_ticket_seq'),
    id VARCHAR(50) NOT NULL UNIQUE,
    tenant_id VARCHAR(50) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    service_id BIGINT NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_tenant_external_id ON trouble_ticket(tenant_id, external_id);
CREATE INDEX idx_tenant_id ON trouble_ticket(tenant_id);
CREATE INDEX idx_status ON trouble_ticket(status);

CREATE TABLE note (
    pk BIGINT PRIMARY KEY DEFAULT nextval('note_seq'),
    id VARCHAR(50) NOT NULL UNIQUE,
    trouble_ticket_pk BIGINT NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trouble_ticket_pk) REFERENCES trouble_ticket(pk) ON DELETE CASCADE
);

CREATE INDEX idx_note_ticket ON note(trouble_ticket_pk);

