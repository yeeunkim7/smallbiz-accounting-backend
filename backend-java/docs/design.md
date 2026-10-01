# 입출금·정산 대사 시스템 설계

Java 백엔드 MVP 설계다. 인증 없는 로컬 개발용이며 가상 거래처·합성 데이터만 사용한다.

선택 버전: **Java 21**, **Spring Boot 4.1.1** (공식 시스템 요구사항: Java 17–26, Gradle 8.14+ 및 9.x). Preview/snapshot은 사용하지 않는다.

## 현재 구현 / 후속 구현

- 구현됨: 애플리케이션 기동, `GET /health`, 거래처, 업무·은행 CSV 업로드, 일별·월별 집계, 날짜별 원본·업로드 이력 조회, **날짜별 검토 상태·메모**.
- 후속: 화면.

원본 CSV 바이트는 보관하지 않는다. 업로드 메타데이터와 정규화된 행만 저장하므로 원본 파일 재다운로드는 제공하지 않는다.

CSV 파서는 **Apache Commons CSV 1.14.0**을 사용한다. 따옴표·필드 안 쉼표·따옴표 안 개행을 `split(",")`로 나누지 않는다. UTF-8 BOM은 선두 3바이트를 제거한다.

## 업무 CSV 업로드

헤더(이름·순서 고정):

`vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id`

- UTF-8, UTF-8 BOM 허용. 최대 5MiB, 데이터 최대 10,000행.
- `source_row_number`는 헤더=1인 CSV 레코드 번호(첫 데이터=2). 물리 텍스트 줄 번호가 아니다.
- 실패 시 이번 요청에서 추가할 `upload_file`/`business_event`는 남지 않는다. 실패 이력은 저장하지 않는다.
- 동일 내용 SHA-256, `source_line_id` 중복은 409. 검증 오류는 400(최대 100개, `truncated`). 크기 초과 413.

CHARGE_EXPECTED는 PREPAID, SETTLEMENT_EXPECTED는 POSTPAID만. REFUND_EXPECTED와 USAGE는 둘 다 허용.

## 은행 CSV 업로드

헤더(이름·순서 고정):

`booked_date,direction,amount,counterparty_name,description,source_line_id`

- UTF-8, BOM 허용. 최대 5MiB, 데이터 최대 10,000행. 해시·한도·오류 100개/`truncated`는 업무 CSV와 같다.
- `direction`은 `IN` 또는 `OUT`. 입금자명으로 거래처를 연결하지 않으며 `vendor_id`를 저장하지 않는다.
- 은행 `source_line_id` UNIQUE는 `bank_transaction`에만 적용한다. 업무 테이블과 같은 문자열 ID여도 중복이 아니다.
- 실패 시 이번 요청의 `upload_file`/`bank_transaction`은 남지 않는다.

## 대사 기준

- 충전 예정(`CHARGE_EXPECTED`)·후불 청구 예정(`SETTLEMENT_EXPECTED`)은 실제 은행 **입금**과 비교한다.
- 현금 환불 예정(`REFUND_EXPECTED`)은 실제 은행 **출금**과 비교한다.
- 서비스 이용(`USAGE`)은 입출금 예정 합계에서 제외한다. 원본 조회에는 남을 수 있다.
- 예정액과 예정일은 외부에서 확정된 값을 고정 CSV로 받는다. 사용액만으로 예정액·예정일을 계산하지 않는다.
- 입금 차이 = 실제 입금 − 예정 입금.
- 출금 차이 = 실제 출금 − 예정 출금.
- 입금과 출금을 상계하지 않는다.
- 일별 **합계**가 같은 것과 개별 거래 확인 완료는 다른 상태다. 합계용 이름에 `MATCHED` / `RECONCILED`를 쓰지 않는다.

선불 거래처에 후불 청구 예정, 후불 거래처에 충전 예정이 오면 업로드 검증 오류(400)다.

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

## CSV 양식

인코딩 UTF-8(BOM 허용), 헤더 1레코드, 날짜 `YYYY-MM-DD`, 금액은 콤마·소수점 없는 정수.

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

## 업로드 중복 및 원자성

- 동일 파일: 내용 SHA-256으로 중복 검사(`upload_file` 전역).
- 같은 파일 안 중복 `source_line_id`는 오류.
- 같은 종류 테이블에 이미 저장된 `source_line_id`가 있으면 **파일 전체 거절**.
- 필수값·날짜·금액·유형 오류가 있으면 파일 전체를 저장하지 않는다.
- 오류 응답 `fieldErrors`: `rowNumber`(헤더=1인 CSV 레코드 번호, 파일 전체 오류는 null), `field`, `message`. 100개를 넘으면 `truncated: true`.
- 애플리케이션 사전 검사와 DB UNIQUE를 함께 사용한다.
- 검토 상태는 이번 범위에서 저장하지 않는다. 업로드 이력과 원본 행만 한 트랜잭션으로 저장한다.

## 일별·월별 집계 조회

`GET /api/v1/reconciliations/daily?from=&to=` (양끝 포함, 최대 366일), `GET /api/v1/reconciliations/monthly?yearMonth=YYYY-MM`.

- 예정 입금: `CHARGE_EXPECTED`·`SETTLEMENT_EXPECTED`, 날짜 `expected_cash_date`. 예정 출금: `REFUND_EXPECTED`. `USAGE` 제외.
- 실제 입금/출금: 은행 `IN`/`OUT`, 날짜 `booked_date`.
- 입금 차이 = 실제 입금 − 예정 입금. 출금 차이 = 실제 출금 − 예정 출금. 상계하지 않음.
- 합계 상태: `TOTAL_EQUAL` / `TOTAL_DIFF`. `MATCHED`/`REVIEWED` 사용 안 함.
- `sourcePresence`: `BOTH` / `BUSINESS_ONLY` / `BANK_ONLY`.
- 금액은 JSON 정수(`long`). PostgreSQL `SUM(bigint)`은 `numeric`이라 집계 SQL에서 `::bigint`로 맞춘다. 응답은 원 단위 정수.
- `::bigint`는 signed 64비트 범위(-9,223,372,036,854,775,808 ~ 9,223,372,036,854,775,807)를 넘으면 오류를 낸다. 조용히 잘리거나 감싸지지 않는다. 행 금액 CHECK는 1 ~ 1,000,000,000,000원이다. Java 월 합계는 `Math.addExact`로 같은 범위를 넘으면 실패한다.
- 저장 집계 테이블 없음. 조회는 원본을 변경하지 않음.
- 업무·은행을 날짜별로 각각 `GROUP BY`한 뒤 날짜로 붙인다. 원본 행 JOIN은 같은 날짜 행 수만큼 금액이 곱해질 수 있다.
- V5: `business_event(expected_cash_date)`는 `WHERE expected_cash_date IS NOT NULL` 부분 인덱스다. 컬럼은 NULL 허용이며 USAGE의 `expected_cash_date IS NULL` 규칙과 맞춘다. `bank_transaction(booked_date)`는 일반 인덱스. 측정 없이 성능 숫자는 주장하지 않음.

합성 예 (2026-09-03 선불 50만+후불 80만 예정 입금, 은행 입금 40만+90만, 환불 예정·출금 각 3만, USAGE 제외. 9/5 업무만 예정 입금 1000. 9/6 은행만 입금 2000. USAGE만 있는 9/7 제외):

| date | expectedIn | actualIn | inDiff | expectedOut | actualOut | outDiff | presence |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 2026-09-03 | 1300000 | 1300000 | 0 | 30000 | 30000 | 0 | BOTH |
| 2026-09-05 | 1000 | 0 | -1000 | 0 | 0 | 0 | BUSINESS_ONLY |
| 2026-09-06 | 0 | 2000 | 2000 | 0 | 0 | 0 | BANK_ONLY |

월별 합성 예 (9/1 예정 입금 100, 9/2 실제 입금 100. 8/31·10/1은 월 밖):

| yearMonth | expectedIn | actualIn | inDiff | expectedOut | actualOut | outDiff | inStatus | dataDayCount | differenceDayCount |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: |
| 2026-09 | 100 | 100 | 0 | 0 | 0 | 0 | TOTAL_EQUAL | 2 | 2 |
| 2026-01 | 0 | 0 | 0 | 0 | 0 | 0 | TOTAL_EQUAL | 0 | 0 |

월 합계 차이가 0이어도 날짜별 차이는 남을 수 있으므로 `differenceDayCount`를 따로 둔다.

## 날짜별 원본·업로드 이력

`GET /api/v1/reconciliations/daily/{date}/business`, `.../bank`: 집계와 같은 날짜·유형. 업무는 `CHARGE_EXPECTED`·`SETTLEMENT_EXPECTED`·`REFUND_EXPECTED`만. `USAGE` 제외. 은행은 해당 `booked_date` 전체. 거래처 자동 연결 없음. 응답은 DTO. 정렬 `source_row_number`, `id`. 빈 날짜는 200·빈 목록.

`GET /api/v1/uploads`, `GET /api/v1/uploads/{uploadId}`: 성공 저장된 이력만. 파일 바이트와 원본 행 전체는 반환하지 않는다.

기존 `GET /reconciliations/daily?from=&to=` 및 `POST /uploads/business|bank`와 경로가 겹치지 않는다. `GET /uploads/{uploadId}`의 `uploadId`는 숫자다.

## 검토 상태

금액 합계(`TOTAL_EQUAL` / `TOTAL_DIFF`)와 사람 검토는 분리한다. 자동으로 `REVIEWED`가 되지 않는다.

`GET/PUT /api/v1/reconciliations/daily/{date}/review`. 날짜당 1행(`daily_review.review_date` UNIQUE). PUT 본문: `status`(`UNREVIEWED`|`REVIEWED`), `memo`(선택, 최대 2000자), `version`(필수). `NEEDS_RECHECK`는 PUT으로 넣을 수 없다.

GET에 행이 없으면 `UNREVIEWED`, `version` 0, 시각·메모 null. GET은 행을 만들지 않는다. 현금 대사 데이터가 없거나 USAGE만 있는 날짜의 PUT은 400.

성공 업로드(파싱·중복 검사를 통과해 원본이 커밋되는 트랜잭션):

- 영향 날짜를 오름차순으로 `INSERT … ON CONFLICT DO NOTHING` 후 `SELECT … FOR UPDATE`.
- 원본이 바뀌므로 상태를 유지하더라도 `updated_at`을 바꿔 버전을 올린다.
- `REVIEWED`만 `NEEDS_RECHECK`. 메모·`last_reviewed_at` 유지.
- 업무: `USAGE`가 아닌 행의 `expected_cash_date`. 은행: `booked_date`.
- 실패·중복 업로드는 이 단계에 도달하지 않는다.

PUT은 같은 행을 `FOR UPDATE`한 뒤 요청 `version`과 비교한다. 다르면 409 `REVIEW_VERSION_CONFLICT`. 업로드가 먼저 커밋되면 예전 화면의 PUT은 거절된다.

일별 집계 `reviewStatus`는 결과 날짜 집합으로 `daily_review`를 한 번 조회한다. 없으면 `UNREVIEWED`.

## 거래처

- 코드는 생성 후 수정하지 않는다.
- 코드: 영문 대문자, 숫자, 하이픈, 밑줄. 최대 32자.
- 이름: 최대 100자. 앞뒤 공백 제거, 공백만이면 거절.
- `settlement_type`: `PREPAID` | `POSTPAID`. DB CHECK와 애플리케이션 검증을 함께 둔다.
- 삭제는 이번 범위에서 제외.
- **후속:** 업무·은행 거래가 생긴 뒤 정산 방식 변경을 허용할지 제한 규칙을 추가한다. 지금은 이름·정산 방식 수정을 막지 않는다.

## 테이블 (후속 포함)

현재 Flyway: V1 vendor, V2 upload_file·business_event, V3 SHA VARCHAR, V4 BANK·bank_transaction, V5 조회용 날짜 인덱스, V6 daily_review.

| 테이블 | 역할 | 유일성 |
| --- | --- | --- |
| vendor | 거래처 마스터 | vendor_code |
| upload_file | 파일 종류, 원본 이름, SHA-256, 시각 | content_sha256 |
| business_event | 업무 원본 1행, source_row_number, vendor FK | source_line_id |
| bank_transaction | 은행 원본 1행, source_row_number. 거래처 FK 없음 | source_line_id |
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

- `POST /api/v1/uploads/business` 201 (multipart `file`)
- `POST /api/v1/uploads/bank` 201 (multipart `file`)
- `GET /api/v1/reconciliations/daily?from=&to=` 200
- `GET /api/v1/reconciliations/monthly?yearMonth=` 200
- `GET /api/v1/reconciliations/daily/{date}/business` 200
- `GET /api/v1/reconciliations/daily/{date}/bank` 200
- `GET /api/v1/uploads` 200, `fileType` 선택, page 기본 0, size 기본 20, 최대 100, `uploadedAt`·`id` 내림차순
- `GET /api/v1/uploads/{uploadId}` 200
- `GET /api/v1/reconciliations/daily/{date}/review` 200
- `PUT /api/v1/reconciliations/daily/{date}/review` 200

후속:

- `GET /vendors/{id}/business-events`

공통 오류: `code`, `message`, 필요 시 `fieldErrors`. 400 입력, 404 없음(`VENDOR_NOT_FOUND`, `UPLOAD_NOT_FOUND`), 409 거래처 코드·동일 파일 해시·`source_line_id` 중복·검토 버전 충돌(`REVIEW_VERSION_CONFLICT`), 413 파일 크기. DB 원문·스택은 응답에 넣지 않는다. UNIQUE가 아닌 무결성 오류를 중복으로 매핑하지 않는다.

## 미해결

- 거래처에 업무·은행 거래가 생긴 뒤 정산 방식 변경 제한은 후속.
