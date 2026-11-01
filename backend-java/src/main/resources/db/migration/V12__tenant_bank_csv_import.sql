-- Independent tenant import history; legacy global upload/bank tables remain untouched.
CREATE TABLE receivable_bank_import (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    bank_account_key VARCHAR(100) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    byte_count INTEGER NOT NULL CHECK (byte_count BETWEEN 1 AND 5242880),
    row_count INTEGER NOT NULL CHECK (row_count BETWEEN 1 AND 10000),
    imported_count INTEGER NOT NULL CHECK (imported_count >= 0),
    duplicate_count INTEGER NOT NULL CHECK (duplicate_count >= 0),
    withdrawal_count INTEGER NOT NULL CHECK (withdrawal_count >= 0),
    created_by UUID NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,bank_account_key,file_sha256),
    CHECK (row_count = imported_count + duplicate_count + withdrawal_count),
    CHECK (bank_account_key ~ '^[A-Za-z0-9._:-]{1,100}$')
);
CREATE TABLE receivable_bank_source_row (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    import_id UUID NOT NULL,
    record_number INTEGER NOT NULL CHECK (record_number BETWEEN 2 AND 10001),
    transaction_id UUID NOT NULL,
    outcome VARCHAR(24) NOT NULL CHECK (outcome IN ('IMPORTED','DUPLICATE','WITHDRAWAL')),
    original_cells JSONB NOT NULL CHECK (jsonb_typeof(original_cells)='array' AND jsonb_array_length(original_cells)=6),
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,import_id,record_number),
    FOREIGN KEY (tenant_id,import_id) REFERENCES receivable_bank_import(tenant_id,id)
);
CREATE TABLE receivable_bank_transaction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    bank_account_key VARCHAR(100) NOT NULL,
    source_line_id VARCHAR(100) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    booked_date DATE NOT NULL CHECK (booked_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    direction VARCHAR(3) NOT NULL CHECK (direction IN ('IN','OUT')),
    amount BIGINT NOT NULL CHECK (amount BETWEEN 1 AND 1000000000000),
    payer_name VARCHAR(100),
    note VARCHAR(1000),
    payment_id UUID,
    first_source_row_id UUID NOT NULL,
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,bank_account_key,source_line_id),
    UNIQUE (tenant_id,payment_id),
    FOREIGN KEY (tenant_id,payment_id) REFERENCES receivable_payment(tenant_id,id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (tenant_id,first_source_row_id) REFERENCES receivable_bank_source_row(tenant_id,id) DEFERRABLE INITIALLY DEFERRED,
    CHECK ((direction='IN' AND payment_id IS NOT NULL) OR (direction='OUT' AND payment_id IS NULL)),
    CHECK (source_line_id ~ '^[A-Za-z0-9._:-]{1,100}$')
);
ALTER TABLE receivable_bank_source_row ADD CONSTRAINT fk_bank_source_transaction
    FOREIGN KEY (tenant_id,transaction_id) REFERENCES receivable_bank_transaction(tenant_id,id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE receivable_payment DROP CONSTRAINT receivable_payment_source_kind_check;
ALTER TABLE receivable_payment ADD COLUMN bank_source_row_id UUID;
ALTER TABLE receivable_payment ADD CONSTRAINT ck_payment_source
    CHECK ((source_kind='MANUAL' AND bank_source_row_id IS NULL) OR (source_kind='BANK_CSV' AND bank_source_row_id IS NOT NULL));
ALTER TABLE receivable_payment ADD CONSTRAINT fk_payment_bank_source
    FOREIGN KEY (tenant_id,bank_source_row_id) REFERENCES receivable_bank_source_row(tenant_id,id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE receivable_bank_import_request (
    tenant_id UUID NOT NULL REFERENCES app_tenant(id),
    request_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    import_id UUID NOT NULL,
    PRIMARY KEY (tenant_id,request_key),
    FOREIGN KEY (tenant_id,import_id) REFERENCES receivable_bank_import(tenant_id,id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_bank_import_page ON receivable_bank_import(tenant_id,created_at DESC,id DESC);
CREATE INDEX ix_bank_source_transaction ON receivable_bank_source_row(tenant_id,transaction_id);
