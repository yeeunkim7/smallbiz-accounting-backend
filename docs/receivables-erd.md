# Receivables 핵심 ERD

아래 그림은 포트폴리오 검토에 필요한 소유권, 핵심 금융 기록, 배분 감사, 후보 실행 및 usage 관계만 표시합니다. 모든 legacy 테이블과 보조 idempotency/import provenance 테이블을 한 그림에 나열하지는 않습니다. 표시한 필드와 관계는 V8–V13 migration을 기준으로 합니다.

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

## 읽는 법과 정확성

`receivable_invoice.amount`와 `receivable_payment.amount`는 원화 양수 정수 `BIGINT`입니다. 배분 line도 정수입니다. 청구 미수금은 원 청구액에서 취소되지 않은 배분의 합계를 빼고, 입금 미배분액도 같은 방식으로 계산합니다. 초과 입금은 미수금 음수로 상계하지 않고 입금 쪽 미할당 잔액으로 남깁니다. 취소는 원 batch/line을 지우지 않고 취소 사실과 감사 이벤트를 추가합니다.

테이블의 `tenant_id`는 소유 범위이고 여러 자식 테이블은 `(tenant_id, id)` 복합 외래 키로 다른 사업체 자원 참조를 막습니다. 그림은 tenant 간 동일 식별자 참조가 되지 않도록 migration에 실제 설정된 복합 FK의 세부 인덱스/제약을 전부 표시하지 않습니다. 단순 ER 화살표는 전체 권한 프로토콜을 대체하지 않습니다.

V12의 `receivable_bank_import`, `receivable_bank_source_row`, `receivable_bank_transaction`, `receivable_bank_import_request`는 CSV 파일 메타데이터, 행별 6개 원문 셀, 중복/출금 결과, 안정 거래 식별자를 기록합니다. 입금 payment는 bank source row와 연결될 수 있습니다. 사용자 제공 안정 거래 ID와 원본 행 번호는 provenance이지 은행 계좌 인증이나 거래 진위 증명은 아닙니다. 출금은 payment로 변환하지 않습니다.

V9의 `receivable_registration_request`, V10의 `receivable_allocation_command`는 tenant/operation/request key 및 요청 hash와 결과를 연결합니다. 이는 명령 재전송을 안전하게 처리하기 위한 보조 관계라 핵심 도메인 그림에는 생략했습니다. V8–V13의 정확한 스키마는 [`db/migration`](../backend-java/src/main/resources/db/migration)을 확인하세요.
