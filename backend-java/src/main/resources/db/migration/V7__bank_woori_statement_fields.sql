ALTER TABLE bank_transaction
    ADD COLUMN booked_at TIMESTAMPTZ,
    ADD COLUMN balance_after BIGINT,
    ADD COLUMN txn_type VARCHAR(100),
    ADD COLUMN branch_name VARCHAR(200);

ALTER TABLE bank_transaction
    ADD CONSTRAINT ck_bank_transaction_balance_after CHECK (
        balance_after IS NULL OR balance_after >= 0
    );

ALTER TABLE bank_transaction
    ADD CONSTRAINT ck_bank_transaction_txn_type_len CHECK (
        txn_type IS NULL OR char_length(txn_type) BETWEEN 1 AND 100
    );

ALTER TABLE bank_transaction
    ADD CONSTRAINT ck_bank_transaction_branch_name_len CHECK (
        branch_name IS NULL OR char_length(branch_name) BETWEEN 1 AND 200
    );
