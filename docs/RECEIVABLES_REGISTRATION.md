# Tenant 소유 거래처·청구·입금 직접 등록

로드맵의 인증 경계 다음 단계다. V9는 새 테이블만 추가하고 기존 `vendor`, `business_event`, `bank_transaction`, 업로드 원본을 tenant 자료로 연결하지 않는다. 기존 대사 API와 V1~V8은 보존한다.

이번 구현은 수동 등록과 읽기다. 청구 응답의 `amount`는 원래 청구 금액이며 미수금·잔액·입금 확인을 뜻하지 않는다. 등록 이후 추가된 배분·취소·잔액 계산은 [배분 계약](RECEIVABLES_ALLOCATION.md), 후보·CSV·사용량·모바일의 현재 상태는 [개발 계획](RECEIVABLES_FREE_MVP_PLAN.md)에 정리한다. 이 문서는 등록 API의 계약만 다룬다. 등록은 대사 실행으로 집계하지 않는다. 이 경로에서는 AI 서비스·조회 도구·모델을 호출하지 않는다.

## API

기본 경로는 `/api/v1/tenants/{tenantId}`다. 모든 요청에 기존 로그인 응답의 `Authorization: Bearer <accessToken>`이 필요하며 모든 서비스 진입점에서 활성 세션과 tenant membership을 다시 확인한다.

| 자원 | 등록 | 목록 | 상세 |
| --- | --- | --- | --- |
| 거래처 | `POST /counterparties` | `GET /counterparties` | `GET /counterparties/{id}` |
| 청구 | `POST /invoices` | `GET /invoices` | `GET /invoices/{id}` |
| 입금 | `POST /payments` | `GET /payments` | `GET /payments/{id}` |

모든 POST는 `Content-Type: application/json`, 필수 `Idempotency-Key` 헤더를 사용하며 성공과 동일 요청 재전송 모두 201로 같은 자원 응답을 반환한다. 이번 단계에는 수정·삭제 API가 없다.

거래처 요청:

```json
{"code":"CUSTOMER_01","name":"합성 거래처"}
```

청구 요청(`counterpartyId`는 이 tenant에서 새로 등록한 거래처 UUID):

```json
{"counterpartyId":"00000000-0000-4000-8000-000000000001","invoiceNumber":"INV-2026-001","amount":"100000","issueDate":"2026-11-01","dueDate":"2026-11-30"}
```

입금 요청:

```json
{"amount":"40000","bookedDate":"2026-11-01","payerName":"합성 입금자","note":"직접 입력한 내역"}
```

입금은 특정 거래처나 청구에 자동 연결하지 않는다. `bookedDate`는 사용자가 입력한 날짜이며 은행 확인을 증명하지 않는다. 미래 날짜도 허용한다.

## 입력과 응답 계약

- 금액은 KRW 정수 **문자열** `"1"`~`"1000000000000"`이다. 정수 숫자 JSON도 거부하며 소수, 지수, 부호, 선행 0, 공백, 0, 음수, 상한 초과를 허용하지 않는다. DB와 Java는 정수 `BIGINT`/`long`, 응답은 정밀도 보존을 위해 문자열을 사용한다. 1조 원 상한은 저장·입력 안전 상한이며 요금제 가격이나 월별 한도가 아니다.
- 날짜는 실제 달력에 존재하는 `YYYY-MM-DD`, 연도 0001~9999만 허용한다. 지급기한은 청구일 이상이어야 한다. 날짜에 시간대 변환을 적용하지 않는다.
- `code`는 앞뒤 공백을 제거한 ASCII 영문자·숫자·`_`·`-` 1~32자이며 대문자로 저장한다. 동일 tenant 안에서 유일하다. `name`은 앞뒤 공백 제거 후 1~100자다.
- `invoiceNumber`는 앞뒤 공백 제거 후 1~64자이고 tenant 안에서 대소문자를 구분해 유일하다. 같은 번호는 다른 tenant에서 사용할 수 있다.
- `payerName`(100자), `note`(1,000자)는 생략/null/공백 문자열을 null로 정규화한다. 모든 텍스트 길이는 Unicode 코드 포인트 기준이다. 제어 문자는 탭·줄바꿈을 제외하고 거부한다.
- 허용되지 않은 필드(`tenantId`, `createdBy`, `sourceKind`, `plan`, 잔액 등), 틀린 JSON 타입, 누락된 필수 필드는 400이다. 서버가 소유권·작성자·원본 유형을 결정한다.
- 자원 응답은 `id`, `tenantId`, 입력 필드, `sourceKind: "MANUAL"`, `createdBy`(등록한 계정 UUID), `createdAt`(UTC ISO 8601)을 포함한다. 청구/입금은 `currency: "KRW"`도 포함한다. 청구 응답에 paid/matching/outstanding 상태나 잔액은 없다.

## 목록과 접근 제어

목록 쿼리는 `page`(0부터 시작, 기본 0)와 `size`(기본 20, 1~100)를 받는다. 응답은 `{"items":[],"page":0,"size":20,"hasNext":false}` 형식이다. `createdAt DESC, id DESC`로 순서를 결정하며 총합·총건수는 이번 계약에 포함하지 않는다. offset 페이지이므로 페이지 사이에 새 등록이 발생하면 항목 위치가 이동할 수 있다.

청구 목록만 선택적으로 `counterpartyId=<UUID>`를 받는다. 필터의 거래처도 현재 tenant 소유인지 확인한다. 타 tenant 자원 UUID를 소유한 tenant 경로에 붙여도 상세/거래처 참조/필터 모두 존재하지 않는 UUID와 같은 404다. 타 tenant 자체 접근은 존재 여부와 무관하게 403이며 미인증은 401이다. 모든 자원 SQL에는 tenant 조건이 있고 청구→거래처 관계는 DB의 `(tenant_id, counterparty_id)` 외래키로도 보호한다. 금융 자료 삭제를 tenant 삭제에 cascade하지 않는다.

## 재전송과 동시성

`Idempotency-Key`는 ASCII 영문자·숫자·`.`·`_`·`:`·`-` 1~100자다. 클라이언트는 하나의 등록 시도에 키 하나를 생성하고 네트워크 오류 후 같은 키와 같은 내용을 재전송해야 한다.

- 키 범위는 `(tenant, 자원 종류, key)`다. 다른 tenant나 다른 종류의 자원에는 같은 키를 사용할 수 있다.
- 정규화한 payload의 SHA-256과 생성된 자원 FK를 등록 행과 같은 트랜잭션에 저장한다. 같은 키/같은 payload는 원래 ID·내용·작성자·생성시각을 돌려준다. 같은 키/다른 payload는 409 `IDEMPOTENCY_CONFLICT`다.
- 동시 동일 요청은 PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`에서 선행 트랜잭션을 기다리고 그 결과를 읽는다. 금융 행과 요청 행은 함께 commit/rollback된다. 저장 실패 후 같은 키로 다시 시도할 수 있다.
- 다른 키로 같은 거래처 코드나 청구 번호를 등록하면 409 `RECEIVABLE_DUPLICATE`다. 실패한 키는 남기지 않는다.
- 같은 금액·같은 날짜의 입금도 다른 키면 별도 정상 입금이다. 금액·날짜 fingerprint로 자동 병합하거나 삭제하지 않는다.
- 요청 기록은 이번 단계에서 자동 만료/삭제하지 않는다. 재전송 보존 기간과 계정 삭제 정책은 운영 출시 전 별도로 정한다.

안전한 오류 JSON은 `code`, `message`를 포함한다. 입력 오류는 400 `INVALID_INPUT`, 자원 없음은 404 `RECEIVABLE_NOT_FOUND`, 저장 실패는 503 `RECEIVABLE_STORAGE_UNAVAILABLE`이며 SQL·입력 금융 내용·타 tenant 데이터는 오류로 반환하거나 기록하지 않는다.

## 검증

전용 `reconciliation_test` DB와 기존 `RejectDevelopmentDatabaseInitializer`를 사용하는 실제 Spring Security/MockMvc 통합 테스트를 추가했다. 금액/날짜 경계, tenant 격리와 DB 복합 FK, 모든 서비스의 세션 재확인, 정규화 재전송, 동시 동일/상이 payload, 동시 청구 번호 충돌, 저장 실패 rollback, 목록/필터를 확인한다. AI 키와 기능 스위치가 설정된 테스트에서도 AI 클라이언트와 조회 도구 호출이 0인지 검증한다.

테스트는 자신이 만든 tenant의 새 금융 테이블을 FK 순서로 정리한 뒤 자신이 만든 계정 행만 정리한다. 운영 스키마에 금융 자료 cascade 삭제를 추가하거나 기존 인증 테스트의 전역 cleanup을 넓히지 않는다. 전체 회귀/패키징 결과는 [AI 작업 기록](AI_REVIEW_WORK.md)에 기록한다.
