# 입출금·정산 대사 시스템 설계

Java 백엔드 MVP 설계다. 인증 없는 로컬 개발용이며 가상 거래처·합성 데이터만 사용한다.

선택 버전: **Java 21**, **Spring Boot 4.1.1** (공식 시스템 요구사항: Java 17–26, Gradle 8.14+ 및 9.x). Preview/snapshot은 사용하지 않는다.

## 현재 구현 / 후속 구현

- 구현됨: 애플리케이션 기동, `GET /health`, 거래처 등록·목록·단건·수정.
- 후속: CSV 업로드, 일별·월별 집계, 원본 조회, 검토 상태. 이 문서의 업로드·대사·검토 규칙은 후속 구현 시 따른다.

## 대사 기준

- 충전 예정(`CHARGE_EXPECTED`)·후불 청구 예정(`SETTLEMENT_EXPECTED`)은 실제 은행 **입금**과 비교한다.
- 현금 환불 예정(`REFUND_EXPECTED`)은 실제 은행 **출금**과 비교한다.
- 서비스 이용(`USAGE`)은 입출금 예정 합계에서 제외한다. 원본 조회에는 남을 수 있다.
- 예정액과 예정일은 외부에서 확정된 값을 고정 CSV로 받는다. 사용액만으로 예정액·예정일을 계산하지 않는다.
- 입금 차이 = 실제 입금 − 예정 입금.
- 출금 차이 = 실제 출금 − 예정 출금.
- 입금과 출금을 상계하지 않는다.
- 일별 **합계**가 같은 것과 개별 거래 확인 완료는 다른 상태다. 합계용 이름에 `MATCHED` / `RECONCILED`를 쓰지 않는다.

선불 거래처에 후불 청구 예정, 후불 거래처에 충전 예정이 오면 업로드 검증 오류로 본다. (후속)

## 대사 범위

- 단일 회사, 단일 은행 계좌, 원화 정수.
- 합성 은행 CSV는 해당 정산 업무 관련 거래만 담는다.
- 대응하는 업무 예정이 없는 은행 거래도 차이로 표시한다.
- 업무 쪽에만 있는 날짜와 은행 쪽에만 있는 날짜도 조회한다.
- 거래처별 기능은 업무 내역 조회까지다. 은행 입금자명만으로 거래처를 확정하지 않으며, 거래처별 일별 대사를 확정 상태로 두지 않는다.
- 인증 없는 로컬 MVP.

## 금액

- Java `long`, PostgreSQL `BIGINT`, JSON 정수.
- `double` / `float` 사용 금지. MVP에서 소수 원화가 없으므로 `BigDecimal`도 쓰지 않는다.
- 검증: 정수만, `> 0`, 상한 1조(1_000_000_000_000). CSV에 소수점이 있으면 행 오류.

## CSV 양식 (후속)

인코딩 UTF-8, 헤더 1행, 날짜 `YYYY-MM-DD`, 금액은 콤마 없는 정수.

### 업무 `business_events.csv`

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| vendor_code | Y | 거래처 마스터 코드 |
| event_type | Y | `CHARGE_EXPECTED` / `SETTLEMENT_EXPECTED` / `REFUND_EXPECTED` / `USAGE` |
| usage_date | `USAGE`는 필수 | 이용일. 대사 기준일이 아님 |
| expected_cash_date | 현금 예정 3종 필수 | 입금 또는 출금 예정일 |
| amount | Y | 원화 정수 |
| note | N | 비고 |
| source_line_id | Y | 원천 거래 ID. **행 번호가 아님** |

### 은행 `bank_transactions.csv`

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| booked_date | Y | 은행 거래일 |
| direction | Y | `IN` / `OUT` |
| amount | Y | 원화 정수 |
| counterparty_name | N | 입금자명·적요. 거래처 확정에 쓰지 않음 |
| description | N | 은행 메모 |
| source_line_id | Y | 합성 데이터의 원천 거래 ID. 실제 은행 파일에 이 ID가 있다고 가정하지 않음 |

## 원천 거래 ID와 원본 행 번호

- `source_line_id`: 파일이 달라도 같은 원천 거래면 유지되는 ID.
- CSV 행 번호를 `source_line_id`로 쓰지 않는다.
- 업로드 파일에서의 위치는 `source_row_number`로 저장한다.
- `source_row_number`는 헤더를 1행으로 세는 CSV 레코드 번호다. 첫 데이터 행은 2.
- 업무 테이블과 은행 테이블 각각에서 `source_line_id` UNIQUE.
- 합성 데이터에는 안정적인 ID를 부여한다. 실제 은행 파일에 동일 필드가 있다고 가정하지 않는다. 실제 은행 연동은 MVP 밖이다.

## 업로드 중복 및 원자성 (후속)

- 동일 파일: 내용 SHA-256으로 중복 검사.
- 같은 파일 안 중복 `source_line_id`는 오류.
- 다른 파일에 이미 저장된 `source_line_id`가 있으면 **파일 전체 거절**.
- 필수값·날짜·금액·유형 오류가 있으면 파일 전체를 저장하지 않는다.
- 오류 응답: `source_row_number`, 필드, 원인.
- 애플리케이션 사전 검사와 DB UNIQUE를 함께 사용한다.
- 업로드 저장과 영향 날짜의 검토 상태 변경은 **같은 트랜잭션**에서 처리한다.

## 검토 상태 (후속)

금액 합계 상태와 사람 검토 상태는 분리한다.

- 합계: 예) `DAILY_IN_TOTAL_EQUAL` / `DAILY_IN_TOTAL_DIFF` (입금·출금 각각).
- 검토: `UNREVIEWED` / `REVIEWED` / `NEEDS_RECHECK`.

업로드 성공 시:

- 영향 날짜의 기존 `REVIEWED` → `NEEDS_RECHECK`. 메모는 유지.
- 영향 날짜 기준: 업무는 `expected_cash_date`, 은행은 `booked_date`.
- `USAGE`만 추가되면 대사 검토 상태를 바꾸지 않는다.
- `UNREVIEWED`는 그대로 둔다.
- 검토 당시 차이 금액만 같아도 완료를 유지하지 않는다. 같은 금액이 양쪽에 추가되면 차이는 그대로여도 원본이 바뀐다.
- GET 조회에서 DB의 검토 상태를 변경하지 않는다.

스냅샷 컬럼으로 완료를 유지하는 방식은 사용하지 않는다.

## 거래처

- 코드는 생성 후 수정하지 않는다.
- 코드: 영문 대문자, 숫자, 하이픈, 밑줄. 최대 32자.
- 이름: 최대 100자. 앞뒤 공백 제거, 공백만이면 거절.
- `settlement_type`: `PREPAID` | `POSTPAID`. DB CHECK와 애플리케이션 검증을 함께 둔다.
- 삭제는 이번 범위에서 제외.
- **후속:** 업무·은행 거래가 생긴 뒤 정산 방식 변경을 허용할지 제한 규칙을 추가한다. 지금은 이름·정산 방식 수정을 막지 않는다.

## 테이블 (후속 포함)

현재 Flyway: `vendor`만 생성한다. 아래는 후속 마이그레이션 대상이다.

| 테이블 | 역할 | 유일성 |
| --- | --- | --- |
| vendor | 거래처 마스터 | vendor_code |
| upload_file | 파일 종류, 원본 이름, SHA-256, 시각 | content_sha256 |
| business_event | 업무 원본 1행, source_row_number, vendor FK | source_line_id |
| bank_transaction | 은행 원본 1행, source_row_number. vendor_id는 MVP에서 NULL | source_line_id |
| daily_review | 날짜별 검토 상태·메모 | review_date |

일별 합계는 조회 시 집계한다. 확정 저장하지 않는다.

기존 Python SQLite 테이블은 변경하지 않는다. Java 앱은 별도 PostgreSQL을 쓴다.

## 주요 API

구현됨:

- `GET /health` — 프로세스 기동 여부만. DB 연결을 보장하지 않는다.
- `POST /api/v1/vendors` 201
- `GET /api/v1/vendors` 200, page 기본 0, size 기본 20, 최대 100, `vendorCode` 오름차순
- `GET /api/v1/vendors/{id}` 200
- `PUT /api/v1/vendors/{id}` 200 (이름·정산 방식만)

후속:

- `POST /uploads/business`, `POST /uploads/bank`
- `GET /uploads`, `GET /uploads/{id}`
- `GET /reconciliations/daily`, `GET /reconciliations/monthly`
- `GET /reconciliations/daily/{date}/business-events`
- `GET /reconciliations/daily/{date}/bank-transactions`
- `GET /vendors/{id}/business-events`
- `PUT /reconciliations/daily/{date}/review`

공통 오류: `code`, `message`, 필요 시 `fieldErrors`. 400 입력, 404 없음, 409 거래처 코드 중복. DB 원문·스택은 응답에 넣지 않는다. UNIQUE가 아닌 무결성 오류를 코드 중복으로 매핑하지 않는다.

## 미해결

- 업로드와 검토 저장이 **동시에** 실행될 때의 일관성(잠금·재시도)은 검토 기능 구현 단계에서 정한다.
