-- Add identity tables only. Legacy financial records intentionally remain unassigned.
CREATE TABLE app_account (
    id UUID PRIMARY KEY,
    login VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at_epoch BIGINT NOT NULL,
    CONSTRAINT ck_account_login_normalized CHECK (login = lower(login))
);
CREATE TABLE app_tenant (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    created_at_epoch BIGINT NOT NULL,
    CONSTRAINT ck_tenant_plan CHECK (plan IN ('FREE', 'STANDARD', 'PRO'))
);
CREATE TABLE app_membership (
    account_id UUID NOT NULL REFERENCES app_account(id),
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    role VARCHAR(20) NOT NULL DEFAULT 'OWNER' CHECK (role IN ('OWNER')),
    PRIMARY KEY (account_id, tenant_id)
);
CREATE TABLE app_session (
    token_hash CHAR(64) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES app_account(id),
    created_at_epoch BIGINT NOT NULL,
    expires_at_epoch BIGINT NOT NULL,
    revoked_at_epoch BIGINT,
    CONSTRAINT ck_session_expiry CHECK (expires_at_epoch > created_at_epoch)
);
CREATE INDEX ix_app_session_account ON app_session(account_id);
CREATE INDEX ix_app_membership_tenant ON app_membership(tenant_id);
