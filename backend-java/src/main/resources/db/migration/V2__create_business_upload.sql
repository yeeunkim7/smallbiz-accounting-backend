CREATE TABLE upload_file (
    id                 BIGSERIAL PRIMARY KEY,
    file_type          VARCHAR(16)  NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    content_sha256     CHAR(64)     NOT NULL,
    row_count          INTEGER      NOT NULL,
    uploaded_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_upload_file_content_sha256 UNIQUE (content_sha256),
    CONSTRAINT ck_upload_file_type CHECK (file_type = 'BUSINESS'),
    CONSTRAINT ck_upload_file_filename_len CHECK (char_length(original_filename) BETWEEN 1 AND 255),
    CONSTRAINT ck_upload_file_sha256 CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_upload_file_row_count CHECK (row_count >= 1 AND row_count <= 10000)
);

CREATE TABLE business_event (
    id                   BIGSERIAL PRIMARY KEY,
    vendor_id            BIGINT       NOT NULL REFERENCES vendor (id),
    upload_file_id       BIGINT       NOT NULL REFERENCES upload_file (id),
    source_line_id       VARCHAR(100) NOT NULL,
    source_row_number    INTEGER      NOT NULL,
    event_type           VARCHAR(32)  NOT NULL,
    usage_date           DATE,
    expected_cash_date   DATE,
    amount               BIGINT       NOT NULL,
    note                 VARCHAR(1000),
    CONSTRAINT uk_business_event_source_line_id UNIQUE (source_line_id),
    CONSTRAINT uk_business_event_upload_row UNIQUE (upload_file_id, source_row_number),
    CONSTRAINT ck_business_event_type CHECK (
        event_type IN ('CHARGE_EXPECTED', 'SETTLEMENT_EXPECTED', 'REFUND_EXPECTED', 'USAGE')
    ),
    CONSTRAINT ck_business_event_amount CHECK (amount >= 1 AND amount <= 1000000000000),
    CONSTRAINT ck_business_event_row_number CHECK (source_row_number >= 2),
    CONSTRAINT ck_business_event_source_line_id CHECK (char_length(source_line_id) BETWEEN 1 AND 100),
    CONSTRAINT ck_business_event_dates CHECK (
        (event_type = 'USAGE' AND usage_date IS NOT NULL AND expected_cash_date IS NULL)
        OR
        (event_type IN ('CHARGE_EXPECTED', 'SETTLEMENT_EXPECTED', 'REFUND_EXPECTED')
            AND expected_cash_date IS NOT NULL)
    )
);
