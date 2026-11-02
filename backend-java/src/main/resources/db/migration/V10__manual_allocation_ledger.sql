-- Immutable confirmation and cancellation facts, separate from registration originals.
CREATE TABLE receivable_allocation_batch (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    reason VARCHAR(1000),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, id)
);
CREATE TABLE receivable_allocation_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    batch_id UUID NOT NULL,
    line_number INTEGER NOT NULL CHECK (line_number BETWEEN 1 AND 100),
    payment_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount BETWEEN 1 AND 1000000000000),
    FOREIGN KEY (tenant_id, batch_id) REFERENCES receivable_allocation_batch(tenant_id, id),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES receivable_payment(tenant_id, id),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES receivable_invoice(tenant_id, id),
    UNIQUE (tenant_id, batch_id, payment_id, invoice_id),
    UNIQUE (tenant_id, batch_id, line_number)
);
CREATE TABLE receivable_allocation_cancellation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    batch_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL CHECK (length(trim(reason)) BETWEEN 1 AND 1000),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, batch_id),
    FOREIGN KEY (tenant_id, batch_id) REFERENCES receivable_allocation_batch(tenant_id, id)
);
CREATE TABLE receivable_allocation_audit (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    batch_id UUID NOT NULL,
    event_type VARCHAR(16) NOT NULL CHECK (event_type IN ('CONFIRMED','CANCELLED')),
    version INTEGER NOT NULL CHECK (version IN (1,2)),
    cancellation_id UUID,
    reason VARCHAR(1000),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_json JSONB NOT NULL,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, batch_id, event_type),
    FOREIGN KEY (tenant_id, batch_id) REFERENCES receivable_allocation_batch(tenant_id, id),
    FOREIGN KEY (tenant_id, cancellation_id) REFERENCES receivable_allocation_cancellation(tenant_id, id),
    CHECK ((event_type='CONFIRMED' AND version=1 AND cancellation_id IS NULL)
        OR (event_type='CANCELLED' AND version=2 AND cancellation_id IS NOT NULL))
);
CREATE TABLE receivable_allocation_command (
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('CONFIRM','CANCEL')),
    request_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_audit_id UUID NOT NULL,
    PRIMARY KEY (tenant_id, operation, request_key),
    FOREIGN KEY (tenant_id, result_audit_id) REFERENCES receivable_allocation_audit(tenant_id, id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_allocation_batch_page ON receivable_allocation_batch(tenant_id, created_at DESC, id DESC);
CREATE INDEX ix_allocation_line_payment ON receivable_allocation_line(tenant_id, payment_id, batch_id);
CREATE INDEX ix_allocation_line_invoice ON receivable_allocation_line(tenant_id, invoice_id, batch_id);
