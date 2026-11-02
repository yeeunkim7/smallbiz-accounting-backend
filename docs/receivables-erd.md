# Receivables 핵심 ERD

청구·입금·배분·취소와 사업체 소유권의 관계입니다.

```mermaid
erDiagram
    APP_ACCOUNT ||--o{ APP_MEMBERSHIP : member
    APP_TENANT ||--o{ APP_MEMBERSHIP : owns
    APP_TENANT ||--o{ RECEIVABLE_COUNTERPARTY : owns
    RECEIVABLE_COUNTERPARTY ||--o{ RECEIVABLE_INVOICE : billed_to
    APP_TENANT ||--o{ RECEIVABLE_PAYMENT : owns
    RECEIVABLE_PAYMENT ||--o{ RECEIVABLE_ALLOCATION_LINE : allocated
    RECEIVABLE_INVOICE ||--o{ RECEIVABLE_ALLOCATION_LINE : receives
    RECEIVABLE_ALLOCATION_BATCH ||--|{ RECEIVABLE_ALLOCATION_LINE : contains
    RECEIVABLE_ALLOCATION_BATCH ||--o| RECEIVABLE_ALLOCATION_CANCELLATION : cancelled_by
    RECEIVABLE_ALLOCATION_BATCH ||--|{ RECEIVABLE_ALLOCATION_AUDIT : records
    APP_TENANT ||--o{ RECEIVABLE_MATCHING_RUN : runs
    RECEIVABLE_MATCHING_RUN ||--o| RECEIVABLE_MATCHING_USAGE_EVENT : meters
    RECEIVABLE_MONTHLY_USAGE ||--o{ RECEIVABLE_MATCHING_USAGE_EVENT : counts

    APP_ACCOUNT {
        uuid id PK
        varchar login UK
        varchar password_hash
        bigint created_at_epoch
    }
    APP_TENANT {
        uuid id PK
        varchar name
        varchar plan
        bigint created_at_epoch
    }
    APP_MEMBERSHIP {
        uuid account_id PK,FK
        uuid tenant_id PK,FK
        varchar role
    }
    RECEIVABLE_COUNTERPARTY {
        uuid id PK
        uuid tenant_id FK
        varchar code
        varchar name
        varchar source_kind
        uuid created_by FK
        timestamptz created_at
    }
    RECEIVABLE_INVOICE {
        uuid id PK
        uuid tenant_id FK
        uuid counterparty_id FK
        varchar invoice_number
        bigint amount
        varchar currency
        date issue_date
        date due_date
        varchar source_kind
        uuid created_by FK
        timestamptz created_at
    }
    RECEIVABLE_PAYMENT {
        uuid id PK
        uuid tenant_id FK
        bigint amount
        varchar currency
        date booked_date
        varchar payer_name
        varchar note
        varchar source_kind
        uuid created_by FK
        timestamptz created_at
        uuid bank_source_row_id FK
    }
    RECEIVABLE_ALLOCATION_BATCH {
        uuid id PK
        uuid tenant_id FK
        varchar reason
        uuid created_by FK
        timestamptz created_at
    }
    RECEIVABLE_ALLOCATION_LINE {
        uuid id PK
        uuid tenant_id FK
        uuid batch_id FK
        integer line_number
        uuid payment_id FK
        uuid invoice_id FK
        bigint amount
    }
    RECEIVABLE_ALLOCATION_CANCELLATION {
        uuid id PK
        uuid tenant_id FK
        uuid batch_id FK
        varchar reason
        uuid created_by FK
        timestamptz created_at
    }
    RECEIVABLE_ALLOCATION_AUDIT {
        uuid id PK
        uuid tenant_id FK
        uuid batch_id FK
        varchar event_type
        integer version
        uuid cancellation_id FK
        varchar reason
        uuid created_by FK
        timestamptz created_at
        jsonb result_json
    }
    RECEIVABLE_MATCHING_RUN {
        uuid id PK
        uuid tenant_id FK
        varchar request_key
        char request_hash
        varchar rule_version
        date from_date
        date to_date
        varchar status
        uuid created_by FK
        timestamptz created_at
        date usage_month
        jsonb result_json
    }
    RECEIVABLE_MONTHLY_USAGE {
        uuid tenant_id PK,FK
        date usage_month PK
        integer completed
        integer limit_count
    }
    RECEIVABLE_MATCHING_USAGE_EVENT {
        uuid tenant_id PK,FK
        uuid run_id PK,FK
        date usage_month FK
        varchar plan
        varchar policy_version
        integer limit_count
        timestamptz approved_at
    }
```

## 데이터 규칙

- 금액은 원화 정수로 저장합니다. 청구 미수와 입금 잔여는 활성 배분을 기준으로 계산합니다.
- 취소는 원본과 배분 행을 삭제하지 않고 별도 이력으로 기록합니다.
- 사업체 조건과 복합 외래 키로 다른 사업체의 자료 참조를 막습니다.
- CSV 출처와 중복 요청 기록은 보조 테이블에서 관리합니다.

[마이그레이션 원본](../backend-java/src/main/resources/db/migration) · [배분 규칙](RECEIVABLES_ALLOCATION.md) · [CSV 등록](RECEIVABLES_IMPORT.md)
