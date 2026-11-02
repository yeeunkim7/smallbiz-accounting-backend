-- Successful bounded synchronous runs only; historical results remain unmetered.
ALTER TABLE receivable_matching_run ADD COLUMN usage_month DATE;
ALTER TABLE receivable_matching_run ADD CONSTRAINT uq_matching_run_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE receivable_matching_run ADD CONSTRAINT ck_matching_run_usage_month
    CHECK (usage_month IS NULL OR extract(day FROM usage_month)=1);

CREATE TABLE receivable_monthly_usage (
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    usage_month DATE NOT NULL CHECK (extract(day FROM usage_month)=1),
    completed INTEGER NOT NULL DEFAULT 0,
    limit_count INTEGER NOT NULL CHECK (limit_count>=0),
    PRIMARY KEY (tenant_id,usage_month),
    CHECK (completed>=0 AND completed<=limit_count)
);
CREATE TABLE receivable_matching_usage_event (
    tenant_id UUID NOT NULL,
    run_id UUID NOT NULL,
    usage_month DATE NOT NULL,
    plan VARCHAR(16) NOT NULL CHECK (plan IN ('FREE','STANDARD','PRO')),
    policy_version VARCHAR(80) NOT NULL,
    limit_count INTEGER NOT NULL CHECK (limit_count>0),
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id,run_id),
    FOREIGN KEY (tenant_id,run_id) REFERENCES receivable_matching_run(tenant_id,id),
    FOREIGN KEY (tenant_id,usage_month) REFERENCES receivable_monthly_usage(tenant_id,usage_month)
);
