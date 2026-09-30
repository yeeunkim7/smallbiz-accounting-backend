ALTER TABLE upload_file DROP CONSTRAINT ck_upload_file_type;
ALTER TABLE upload_file ADD CONSTRAINT ck_upload_file_type CHECK (file_type IN ('BUSINESS', 'BANK'));

CREATE TABLE bank_transaction (
    id                 BIGSERIAL PRIMARY KEY,
    upload_file_id     BIGINT       NOT NULL REFERENCES upload_file (id),
    source_line_id     VARCHAR(100) NOT NULL,
    source_row_number  INTEGER      NOT NULL,
    booked_date        DATE         NOT NULL,
    direction          VARCHAR(8)   NOT NULL,
    amount             BIGINT       NOT NULL,
    counterparty_name  VARCHAR(200),
    description        VARCHAR(1000),
    CONSTRAINT uk_bank_transaction_source_line_id UNIQUE (source_line_id),
    CONSTRAINT uk_bank_transaction_upload_row UNIQUE (upload_file_id, source_row_number),
    CONSTRAINT ck_bank_transaction_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_bank_transaction_amount CHECK (amount >= 1 AND amount <= 1000000000000),
    CONSTRAINT ck_bank_transaction_row_number CHECK (source_row_number >= 2),
    CONSTRAINT ck_bank_transaction_source_line_id CHECK (char_length(source_line_id) BETWEEN 1 AND 100),
    CONSTRAINT ck_bank_transaction_counterparty_len CHECK (
        counterparty_name IS NULL OR char_length(counterparty_name) BETWEEN 1 AND 200
    ),
    CONSTRAINT ck_bank_transaction_description_len CHECK (
        description IS NULL OR char_length(description) BETWEEN 1 AND 1000
    )
);
