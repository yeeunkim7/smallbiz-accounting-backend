CREATE INDEX idx_business_event_expected_cash_date
    ON business_event (expected_cash_date)
    WHERE expected_cash_date IS NOT NULL;

CREATE INDEX idx_bank_transaction_booked_date
    ON bank_transaction (booked_date);
