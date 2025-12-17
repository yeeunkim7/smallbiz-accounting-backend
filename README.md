# smallbiz-accounting-backend
전표 기반 회계 처리를 구현한 소규모 사업자용 백엔드 서비스
## ERD

```mermaid
erDiagram

  USER ||--o{ ACCOUNT_SUBJECT : has
  USER ||--o{ VENDOR : owns
  USER ||--o{ EXPENSE : owns
  USER ||--o{ SALE : owns
  USER ||--o{ PURCHASE : owns
  USER ||--o{ JOURNAL_ENTRY : owns
  USER ||--o{ EVIDENCE : uploads
  USER ||--o{ VAT_SUMMARY : has

  EXPENSE ||--o| JOURNAL_ENTRY : generates
  SALE ||--o| JOURNAL_ENTRY : generates
  PURCHASE ||--o| JOURNAL_ENTRY : generates

  JOURNAL_ENTRY ||--o{ JOURNAL_LINE : contains
  ACCOUNT_SUBJECT ||--o{ JOURNAL_LINE : used_in

  EXPENSE ||--o{ EXPENSE_EVIDENCE : attaches
  EVIDENCE ||--o{ EXPENSE_EVIDENCE : attached_to

  SALE ||--o{ SALE_EVIDENCE : attaches
  EVIDENCE ||--o{ SALE_EVIDENCE : attached_to

  PURCHASE ||--o{ PURCHASE_EVIDENCE : attaches
  EVIDENCE ||--o{ PURCHASE_EVIDENCE : attached_to

  USER {
    bigint id PK
    varchar email UK
    varchar name
    timestamp created_at
  }

  VENDOR {
    bigint id PK
    bigint user_id FK
    varchar name
    varchar business_no
    timestamp created_at
  }

  ACCOUNT_SUBJECT {
    bigint id PK
    bigint user_id FK
    varchar code
    varchar name
    enum type
    boolean is_active
    timestamp created_at
  }

  EXPENSE {
    bigint id PK
    bigint user_id FK
    date transaction_date
    bigint vendor_id FK
    bigint account_subject_id FK
    decimal net_amount
    decimal vat_amount
    decimal gross_amount
    enum status
    bigint journal_entry_id FK
    timestamp created_at
    timestamp confirmed_at
  }

  SALE {
    bigint id PK
    bigint user_id FK
    date transaction_date
    bigint vendor_id FK
    decimal net_amount
    decimal vat_amount
    decimal gross_amount
    enum status
    bigint journal_entry_id FK
    timestamp created_at
    timestamp confirmed_at
  }

  PURCHASE {
    bigint id PK
    bigint user_id FK
    date transaction_date
    bigint vendor_id FK
    decimal net_amount
    decimal vat_amount
    decimal gross_amount
    enum status
    bigint journal_entry_id FK
    timestamp created_at
    timestamp confirmed_at
  }

  JOURNAL_ENTRY {
    bigint id PK
    bigint user_id FK
    date entry_date
    enum status
    enum source_type
    bigint source_id
    timestamp created_at
    timestamp posted_at
  }

  JOURNAL_LINE {
    bigint id PK
    bigint journal_entry_id FK
    bigint account_subject_id FK
    enum direction
    decimal amount
    int line_order
  }

  EVIDENCE {
    bigint id PK
    bigint user_id FK
    enum type
    date issued_at
    bigint vendor_id FK
    varchar file_uri
    timestamp uploaded_at
  }

  VAT_SUMMARY {
    bigint id PK
    bigint user_id FK
    varchar year_month
    decimal output_vat
    decimal input_vat
    decimal net_vat
    timestamp calculated_at
  }
