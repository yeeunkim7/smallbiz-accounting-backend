CREATE TABLE daily_review (
    review_date        DATE           PRIMARY KEY,
    status             VARCHAR(16)    NOT NULL,
    memo               VARCHAR(2000),
    last_reviewed_at   TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL,
    CONSTRAINT ck_daily_review_status CHECK (
        status IN ('UNREVIEWED', 'REVIEWED', 'NEEDS_RECHECK')
    ),
    CONSTRAINT ck_daily_review_memo_len CHECK (
        memo IS NULL OR char_length(memo) BETWEEN 1 AND 2000
    ),
    CONSTRAINT ck_daily_review_version CHECK (version >= 0)
);
