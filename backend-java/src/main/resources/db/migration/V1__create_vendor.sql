CREATE TABLE vendor (
    id              BIGSERIAL PRIMARY KEY,
    vendor_code     VARCHAR(32)  NOT NULL,
    vendor_name     VARCHAR(100) NOT NULL,
    settlement_type VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_vendor_vendor_code UNIQUE (vendor_code),
    CONSTRAINT ck_vendor_settlement_type CHECK (settlement_type IN ('PREPAID', 'POSTPAID'))
);
