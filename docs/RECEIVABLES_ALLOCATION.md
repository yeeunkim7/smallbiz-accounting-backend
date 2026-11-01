# 수동 배분·취소·현재 잔액

거래처·청구·입금 [등록 API](RECEIVABLES_REGISTRATION.md)에 이어 수동 배분을 추가한다. V10은 V9 원본 테이블이나 migration을 변경하지 않고 batch·line·cancellation·audit·command 테이블을 추가한다. 원래 청구액·입금액은 유지하고 취소 이력을 추가하여 활성 배분만 계산에 반영한다. 기존 등록 응답 계약은 그대로다.

이번 기능은 사용자가 명시한 청구/입금 사이의 수동 확인이다. 날짜·입금자명·거래처명이 다르더라도 사용자가 지정한 배분을 허용하며, 이름이나 날짜로 자동 연결하지 않는다. 한 입금을 여러 거래처의 청구에 배분할 수 있다. 은행 연동·실제 돈 이동·환불·대손 처리가 아니다. 후보, import, 월별 성공 실행 사용량, Expo는 별도 기능이며 수동 확정·취소는 quota를 쓰거나 AI를 호출하지 않는다.

## API 계약

기본 경로는 `/api/v1/tenants/{tenantId}`이고 기존 Bearer 로그인이 필요하다. 모든 서비스 진입점은 활성 세션과 tenant membership을 확인하며, 모든 읽기/쓰기 대상은 tenant 범위다.

| 요청 | 동작 |
| --- | --- |
| `POST /allocations` | 1~100개 배분 행을 모두 확정하거나 모두 rollback. 성공/동일 재전송 201 |
| `POST /allocations/{id}/cancel` | batch 전체를 사유와 함께 취소. 성공/동일 재전송 200 |
| `GET /allocations/{id}` | 현재 상태와 원래 행·확정/취소 감사 이력 |
| `GET /allocations?page=0&size=20` | 취소 이력도 포함한 목록. 크기 1~100, 최대 한 페이지 100개 batch |
| `GET /invoices/{id}/balance?asOf=2026-11-02` | 현재 청구 배분액·미수금·지급기한 초과액 |
| `GET /payments/{id}/balance` | 현재 입금 배분액·미할당액 |
| `GET /receivables/summary?asOf=2026-11-02` | tenant 전체의 현재 합계·원본 건수·활성 배분 행 수 |

확정/취소 POST는 필수 `Idempotency-Key`와 JSON body를 사용한다. 키 형식은 등록과 같은 ASCII 영문자·숫자·`.`·`_`·`:`·`-` 1~100자다.

확정 예시:

```json
{
  "lines": [
    {"paymentId":"00000000-0000-4000-8000-000000000001","invoiceId":"00000000-0000-4000-8000-000000000002","amount":"30000"},
    {"paymentId":"00000000-0000-4000-8000-000000000001","invoiceId":"00000000-0000-4000-8000-000000000003","amount":"70000"}
  ],
  "reason":"직접 확인한 묶음 입금"
}
```

금액은 등록과 같은 KRW 정수 문자열 `"1"`~`"1000000000000"`이며 숫자 JSON·소수·부호·공백·상한 초과를 거부한다. 같은 요청 안에서 `(paymentId, invoiceId)` 쌍은 한 번만 허용한다. `reason`은 선택이며 앞뒤 공백 제거 후 최대 1,000자다. 허용되지 않은 필드와 잘못된 타입은 400이다.

취소 예시:

```json
{"reason":"잘못 선택한 입금이므로 취소 후 다시 확정"}
```

취소 사유는 공백 제거 후 1~1,000자가 필수다. 부분 취소/원본 덮어쓰기는 없다. 수정하려면 **기존 batch를 사유와 함께 취소한 후 새 batch를 명시적으로 확정**한다. 두 작업은 각각의 트랜잭션이며 원자적 replace API는 이번 범위에 없다.

## 응답과 불변 이력

배분 응답은 `id`, `tenantId`, `version`, `status`, `lines`, 확정 `reason`·`createdBy`·`createdAt`, 선택 `cancellation`, `audit`를 포함한다. 각 행은 불변 ID와 payment/invoice ID, 문자열 amount를 포함한다. 서버 세션에서 작성자를 결정하고 시각은 UTC ISO 8601로 기록한다.

- 확정 결과: `version: 1`, `status: "CONFIRMED"`, 확정 감사 이벤트 1개.
- 취소 후 상세: `version: 2`, `status: "CANCELLED"`, 원래 행과 확정 작성자/시각은 그대로이고 cancellation 사유/작성자/시각 및 취소 감사 이벤트가 추가된다.
- 원본 청구/입금, 배분 행, 확정/취소 감사 행은 서비스에서 UPDATE/DELETE하지 않는다. 취소 여부는 유일한 별도 cancellation 행의 존재로 결정한다. 재배분은 새 batch로 남는다.

명령의 재전송 결과와 현재 상태를 구분한다. **확정 후 나중에 취소되었어도 원래 확정 키로 재전송하면 원래 version 1 응답 snapshot을 반환한다. 현재 상태는 `GET /allocations/{id}`로 조회한다.** 취소 재전송도 최초 취소의 actor/time/audit ID와 응답을 그대로 반환한다.

키 범위는 `(tenant, CONFIRM 또는 CANCEL, key)`다. 정규화한 요청 SHA-256과 불변 감사 결과 FK를 같은 트랜잭션에 저장한다. 행 순서는 의미가 없어 payment/invoice UUID 순으로 정규화하므로 순서를 바꿔 재전송해도 동일하다. 같은 키/다른 내용은 409 `IDEMPOTENCY_CONFLICT`이며 취소 hash에는 batch ID도 포함한다. 다른 키로 이미 취소된 batch를 취소하면 409 `ALLOCATION_ALREADY_CANCELLED`이며 감사 행을 추가하지 않는다. 실패한 명령은 키·금융 행·감사 행이 모두 rollback되어 같은 키로 재시도할 수 있다.

## 잔액·기한과 합계

청구 잔액은 `outstandingAmount = amount - allocatedAmount`, 입금 잔여는 `unallocatedAmount = amount - allocatedAmount`다. `allocatedAmount`는 취소되지 않은 배분 행의 합계다. 초과 입금은 남은 미할당액으로 표현하며 청구 잔액을 음수로 만들지 않는다.

청구 잔액 응답은 `invoiceId`, `tenantId`, 원래 `amount`, `allocatedAmount`, `outstandingAmount`, `overdueAmount`, `dueDate`, `asOf`, `status`를 반환한다. 상태는 활성 배분액으로 파생한 `UNPAID`/`PARTIAL`/`PAID`다. 입금 잔액 응답의 상태는 `UNALLOCATED`/`PARTIAL`/`ALLOCATED`다. 모두 사용자 확정 배분의 계산 상태이며 은행의 별도 입금 검증 결과가 아니다.

`asOf`는 필수 실제 날짜 문자열 `YYYY-MM-DD`(연도 0001~9999)이다. `dueDate < asOf`인 청구의 현재 남은 금액만 overdue이며 기한 당일은 overdue가 아니다. **`asOf`는 지급기한 비교 기준일일 뿐 과거 회계 snapshot이 아니다.** 모든 잔액/합계는 현재 활성 배분을 사용한다. `asOf` 이후 등록·확정·취소된 기록을 역사적으로 되돌리거나 해당 날짜 전의 청구/입금만 선택하지 않는다. summary의 `allocationState: "CURRENT"`가 이 계약을 표시한다.

summary는 `invoiceCount`, `paymentCount`, `activeAllocationLineCount`와 문자열 `invoiceAmount`, `allocatedAmount`, `outstandingAmount`, `overdueAmount`, `paymentAmount`, `unallocatedAmount`를 반환한다. 데이터가 없으면 건수/금액이 0이다. 원본 건수와 활성 배분 행 수를 함께 제공하여 “자료 없음”, “아직 배분 없음”, “배분 후 잔액 0”을 구분한다. 합계 0만으로 개별 입금이 모두 확인됐다고 판단하지 않는다.

Java 입력/행 금액은 정확한 long, 요청별 합산은 `Math.addExact`, 저장 합계는 PostgreSQL numeric과 Java BigInteger로 처리한다. tenant 전체 합계는 long으로 잘라내지 않으며 금액 응답은 모두 십진 문자열이다. summary와 개별 잔액은 각각 하나의 SQL statement로 같은 시점의 자료를 읽고 캐시된 잔액을 저장하지 않는다. 여러 쿼리가 필요한 배분 상세/목록은 read-only repeatable-read snapshot을 사용한다.

## 동시성과 DB 경계

확정·취소의 공통 순서는 command 키 확보 → 모든 payment UUID 오름차순 잠금 → 모든 invoice UUID 오름차순 잠금이다. UUID 정렬은 표준 UUID 문자열 순서다. 확정은 부모 잠금을 모두 확보한 다음 현재 활성 합계를 다시 읽어 양쪽 가용액을 검사한다. 같은 청구나 입금의 경쟁 확정이 원금을 초과하면 후행 요청은 409 `ALLOCATION_EXCEEDS_AVAILABLE`로 전체 rollback한다.

취소는 불변 batch 행들을 먼저 읽어 부모 집합을 찾고 같은 순서로 부모를 잠근 뒤 취소 사실을 다시 확인한다. 확정/취소가 서로 반대 잠금 순서를 갖거나 취소 상태를 먼저 잠그지 않는다. 동일 키 동시 요청은 PostgreSQL conflict 처리에서 선행 commit을 기다린 뒤 저장된 감사 결과를 재생한다. command의 deferred FK는 감사 결과 없는 명령이 commit되지 않게 한다.

DB의 양수 금액·행 번호·쌍 유일성·batch당 한 번 취소 제약과 `(tenant,id)` 복합 FK가 기본 경계를 지킨다. 여러 행의 합산 상한은 서비스의 부모 잠금/재검증 프로토콜로 보장한다. 향후 배분 writer를 추가할 때도 같은 프로토콜을 따라야 한다. 기존 전역 금융 데이터나 AI 도구를 배분 경로에서 조회하지 않는다.

미인증은 401, 타 tenant 경로는 403이다. 현재 tenant에 없는 batch/invoice/payment ID와 다른 tenant의 ID는 같은 404 `RECEIVABLE_NOT_FOUND`다. 오류 JSON은 `code`, `message`만 반환하며 DB 저장 실패는 503 `ALLOCATION_STORAGE_UNAVAILABLE`로 입력 금융 내용이나 SQL을 노출하지 않는다. 목록은 등록과 같은 offset 페이지이며 동시 새 등록 사이의 고정 페이지 snapshot은 보장하지 않는다.

## 검증

새 통합 테스트는 전용 `reconciliation_test`와 기존 개발 DB 거부 initializer, 실제 security filter를 사용한다. 부분 40,000+60,000, 묶음 30,000+70,000, 양쪽/요청 합산 초과, 잘못된 입력·교차 tenant FK, 취소/원응답 재전송, 늦은 감사 저장 실패 rollback, 동일/상이 키 경쟁, 동일 청구·입금 경쟁, 반대 입력 순서의 다대다 확정/취소, 확정과 취소 경쟁, 지급기한 경계와 summary 갱신을 확인한다. 설정된 합성 AI 키가 있어도 AI client/query 도구 무호출을 검사한다.

테스트는 자기 tenant의 V10 테이블을 FK 순서대로 정리한 뒤 자기 V9/계정 행을 정리하며 production cascade 삭제는 추가하지 않는다. 공유 입력 검증을 추출했으므로 기존 등록 통합 테스트도 함께 실행한다. 전체 회귀·패키징 결과는 [작업 기록](AI_REVIEW_WORK.md)에 별도로 기록한다.
