-- New tenant-owned records only. No legacy vendor/event/bank rows are reassigned.
CREATE TABLE receivable_counterparty (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    source_kind VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (source_kind = 'MANUAL'),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, id),
    CONSTRAINT uk_receivable_counterparty_code UNIQUE (tenant_id, code),
    CHECK (code ~ '^[A-Z0-9_-]{1,32}$'),
    CHECK (length(trim(name)) BETWEEN 1 AND 100)
);
CREATE TABLE receivable_invoice (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    counterparty_id UUID NOT NULL,
    invoice_number VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount BETWEEN 1 AND 1000000000000),
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW' CHECK (currency = 'KRW'),
    issue_date DATE NOT NULL CHECK (issue_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    due_date DATE NOT NULL CHECK (due_date BETWEEN issue_date AND DATE '9999-12-31'),
    source_kind VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (source_kind = 'MANUAL'),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, id),
    CONSTRAINT uk_receivable_invoice_number UNIQUE (tenant_id, invoice_number),
    FOREIGN KEY (tenant_id, counterparty_id) REFERENCES receivable_counterparty(tenant_id, id),
    CHECK (length(trim(invoice_number)) BETWEEN 1 AND 64)
);
CREATE TABLE receivable_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    amount BIGINT NOT NULL CHECK (amount BETWEEN 1 AND 1000000000000),
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW' CHECK (currency = 'KRW'),
    booked_date DATE NOT NULL CHECK (booked_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    payer_name VARCHAR(100),
    note VARCHAR(1000),
    source_kind VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (source_kind = 'MANUAL'),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, id)
);
-- Written in the same transaction as its result. Resource FKs prevent dangling replays.
CREATE TABLE receivable_registration_request (
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('COUNTERPARTY','INVOICE','PAYMENT')),
    request_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    counterparty_id UUID,
    invoice_id UUID,
    payment_id UUID,
    PRIMARY KEY (tenant_id, operation, request_key),
    FOREIGN KEY (tenant_id, counterparty_id) REFERENCES receivable_counterparty(tenant_id, id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES receivable_invoice(tenant_id, id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (tenant_id, payment_id) REFERENCES receivable_payment(tenant_id, id) DEFERRABLE INITIALLY DEFERRED,
    CHECK ((operation = 'COUNTERPARTY' AND counterparty_id IS NOT NULL AND invoice_id IS NULL AND payment_id IS NULL)
        OR (operation = 'INVOICE' AND invoice_id IS NOT NULL AND counterparty_id IS NULL AND payment_id IS NULL)
        OR (operation = 'PAYMENT' AND payment_id IS NOT NULL AND counterparty_id IS NULL AND invoice_id IS NULL))
);
CREATE INDEX ix_receivable_counterparty_page ON receivable_counterparty(tenant_id, created_at DESC, id DESC);
CREATE INDEX ix_receivable_invoice_page ON receivable_invoice(tenant_id, created_at DESC, id DESC);
CREATE INDEX ix_receivable_invoice_counterparty_page ON receivable_invoice(tenant_id, counterparty_id, created_at DESC, id DESC);
CREATE INDEX ix_receivable_payment_page ON receivable_payment(tenant_id, created_at DESC, id DESC);
