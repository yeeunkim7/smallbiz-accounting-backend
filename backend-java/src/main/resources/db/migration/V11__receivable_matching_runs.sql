-- Read-only rule runs: no allocation/usage mutation and no reassignment of legacy data.
CREATE TABLE receivable_matching_run (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    request_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    from_date DATE NOT NULL CHECK (from_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    to_date DATE NOT NULL CHECK (to_date BETWEEN from_date AND DATE '9999-12-31' AND to_date-from_date <= 365),
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','SUCCESS')),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_json JSONB,
    UNIQUE (tenant_id, request_key),
    CHECK ((status='RUNNING' AND result_json IS NULL) OR (status='SUCCESS' AND result_json IS NOT NULL))
);
CREATE INDEX ix_matching_run_page ON receivable_matching_run(tenant_id, created_at DESC, id DESC);
