# 규칙 후보 실행과 불변 결과

청구·입금 등록과 수동 배분 다음 기능이다. V11은 tenant 소유 matching run과 결과 snapshot만 추가한다. 기존 V1~V10·등록 원본·배분/취소/감사 이력을 변경하지 않는다. 이 경로는 AI·기존 전역 조회 도구·은행/CSV import·배분 확정을 호출하지 않는다.

**내부 사전 출시용 API다.** V13의 [월별 성공 실행 사용량](RECEIVABLES_USAGE.md)이 현재 같은 트랜잭션에서 시행된다. 아래 계산량 상한은 별도 안전 경계이며 유료 플랜 약속이 아니다. 모바일·공개 운영 준비는 남아 있다.

## API

기본 경로는 `/api/v1/tenants/{tenantId}`이며 기존 Bearer 인증을 사용한다. 모든 public 서비스 진입점이 활성 세션·membership을 확인한다.

| 요청 | 결과 |
| --- | --- |
| `POST /matching-runs` | 범위의 현재 자료로 규칙 후보를 계산·저장. 성공/동일 재전송 201 |
| `GET /matching-runs/{id}` | 실행 당시 저장한 전체 snapshot |
| `GET /matching-runs?page=0&size=20` | 실행 metadata 목록. 크기 1~100, 최신 생성시각·ID 순 |

POST는 `Content-Type: application/json`과 필수 `Idempotency-Key`를 받는다. 키 형식은 기존 등록/배분과 같은 ASCII 영문자·숫자·`.`·`_`·`:`·`-` 1~100자다.

```json
{"from":"2026-11-01","to":"2026-11-30"}
```

`from`, `to`는 실제 날짜인 엄격한 `YYYY-MM-DD`, 연도 0001~9999다. 양끝을 포함해 최대 366일이며 `from <= to`여야 한다. 다른 필드, 숫자 날짜, 공백을 붙인 날짜, 누락 필드는 400이다.

## 읽는 자료와 계산량 경계

- 입금: 현재 tenant에서 `from <= bookedDate <= to`이고 현재 미할당액이 양수인 입금.
- 청구: 현재 tenant에서 `issueDate <= to`이고 현재 미수금이 양수인 청구. `from` 이전의 오래된 미수 청구도 포함한다.
- 현재 배분은 취소되지 않은 배분 행만 합산한다. 원래 금액에서 활성 배분액을 빼고, 원금 초과/음수 등 저장 불변식 위반은 503으로 거부한다. 원금이나 기존 배분을 수정하지 않는다.
- 두 자료 집합·배분 합계·이름·날짜 증거는 **하나의 PostgreSQL SQL statement snapshot**에서 읽는다. 후보 계산을 위해 청구/입금 부모를 잠그지 않는다.

설정은 다음과 같다. 잘못된 설정은 시작 시 거부한다.

| 설정 | 기본값 | 허용값 |
| --- | --- | --- |
| `app.receivables.matching.max-payments` | 200 | 1~1,000 |
| `app.receivables.matching.max-invoices` | 200 | 1~1,000 |
| `app.receivables.matching.top-per-payment` | 5 | 1~20 |

입금/청구 각각 상한+1개까지 읽어 초과를 확인한다. 하나라도 넘으면 전체 실행을 422 `MATCHING_SOURCE_LIMIT`로 거부하고 run을 남기지 않는다. 처음 N개만으로 전체 대사를 수행했다고 표시하지 않는다. 오래된 미수 청구도 청구 상한에 포함되므로 입금 날짜 범위만 줄여도 항상 해결되는 것은 아니다.

## 후보 규칙과 정렬

`ruleVersion`은 `manual-review-rules-v1`이다. 다음 중 하나가 참인 청구만 해당 입금의 후보가 된다.

1. 현재 입금 미할당액과 청구 미수금이 정확히 같다.
2. 비어 있지 않은 정규화 입금자명과 거래처명이 정확히 같다.

이름은 Unicode NFKC → Unicode 공백 연속을 한 칸으로 축약 → 양끝 제거 → `Locale.ROOT` 소문자 순으로 정규화한다. 비 ASCII 이름도 보존하며 부분 문자열·별칭·유사도·추측을 사용하지 않는다. 원래 이름과 정규화한 이름을 모두 snapshot에 남긴다. 이름 일치는 상대의 법적 동일성이나 은행 입금자 소유권을 증명하지 않는다.

후보마다 `suggestedAmount = min(입금 미할당액, 청구 미수금)`을 문자열로 제공한다. 같은 이름의 부분 입금/초과 입금도 후보가 될 수 있다. 여러 후보는 **개별 검토 대안**이며 제안 금액을 전부 더해서 자동 확정할 배분 계획이 아니다. 부분집합 탐색·묶음 조합·자동 확정은 없다.

정렬 점수는 확률이 아니다.

| 근거 코드 | 점수 |
| --- | --- |
| `EXACT_REMAINING_AMOUNT` | 100 |
| `NORMALIZED_NAME_EXACT` | 50 |
| `BOOKED_ON_OR_AFTER_ISSUE_DATE` | 10 |

날짜 조건만으로 후보를 만들지 않는다. 점수 내림차순 → `abs(bookedDate - dueDate)` 일수 오름차순 → invoice UUID 표준 문자열 오름차순으로 정렬한다. 날짜 차이는 long 정수 일수이며 금액 계산에는 부동소수점을 쓰지 않는다.

경고 코드는 다음과 같다. 경고가 있어도 사용자가 직접 판단할 후보로 남을 수 있다.

| 경고 | 의미 |
| --- | --- |
| `REMAINING_AMOUNT_MISMATCH` | 양쪽 현재 잔액이 달라 부분 배분 검토 필요 |
| `PAYER_NAME_MISSING` / `COUNTERPARTY_NAME_MISSING` | 비교할 이름이 비어 있음 |
| `NAME_MISMATCH` | 이름이 서로 다름 |
| `PAYMENT_BEFORE_ISSUE_DATE` | 입금일이 청구일 이전 |
| `PAYMENT_AFTER_DUE_DATE` | 입금일이 지급기한보다 늦음 |

날짜 차이/이름 차이를 자동 거절하거나 실제 입금 누락이라고 단정하지 않는다.

## 결과 읽기와 재전송

전체 결과는 run ID·tenant ID·ruleVersion·범위·`SUCCESS`·actor·UTC 생성시각과 다음 필드를 포함한다.

- `sourceCounts`: 선택된 미할당 입금 수와 미수 청구 수.
- `dataPresence`: `NO_ELIGIBLE_SOURCES`, `PAYMENTS_ONLY`, `INVOICES_ONLY`, `BOTH`. 이 값은 이번 범위/잔액 필터에 해당하는 신규 tenant 자료의 존재만 설명한다. 원래 자료나 업로드 파일이 없다는 뜻이 아니다.
- `invoices`: 선택된 모든 청구의 ID·거래처 ID·청구번호·원래 금액·활성 배분액·미수금·청구일/기한·이름 증거 snapshot.
- `payments`: 입금 원래 금액·활성 배분액·미할당액·입금일·이름 증거와 각 입금의 후보 목록. 후보의 `invoiceId`로 위 청구 증거를 찾는다.
- 입금마다 후보가 없으면 `status: "NEEDS_MANUAL_REVIEW"`, 있으면 `CANDIDATES_AVAILABLE`다. 후보가 없다는 사실은 미입금·누락 파일·채무 불이행을 증명하지 않는다.
- `totalCandidateCount`는 top 상한 적용 전 후보 수, `candidatesTruncated`는 표시 후보가 줄었는지 나타낸다. 전체 소스를 잘라내는 것과 입금별 표시 상한을 구분한다.

금액은 모두 정확한 십진 문자열이다. 같은 source snapshot과 ruleVersion/config이면 순서·근거·제안 금액이 결정적으로 같으며 실제 은행 검증 확률이나 자동 완료 상태는 제공하지 않는다. 목록 API는 전체 금융 snapshot을 반복 반환하지 않고 범위·버전·시각·작성자·sourceCounts/dataPresence metadata만 반환한다. offset 목록은 동시 새 실행 사이의 고정 페이지를 보장하지 않는다.

키는 `(tenant, key)` 범위다. 엄격한 날짜를 정규화한 range의 SHA-256을 비교한다. 같은 키/같은 범위는 **최초 run ID와 저장 snapshot 그대로** 반환하며 다른 범위는 409 `IDEMPOTENCY_CONFLICT`다. JSON 필드 순서는 의미가 없다. 같은 키의 병렬 INSERT는 선행 트랜잭션을 기다리고 그 결과를 읽어 한 run만 commit한다.

run의 RUNNING 행·월 usage 잠금/승인·현재 자료 읽기·SUCCESS snapshot·usage event/counter 저장은 하나의 트랜잭션이며 실패하면 모두 rollback한다. 외부에서 관측 가능한 정상 결과는 SUCCESS뿐이다. 저장된 SUCCESS snapshot을 갱신하는 API는 없다. 동일 키의 재전송도 현재 권한은 다시 확인한다.

**저장 후보는 이후 배분·취소·새 등록으로 오래될 수 있다.** detail/동일 키 재전송은 재계산하지 않는다. 새 실행 키로 갱신할 수 있고, 수동 확정은 기존 [배분 API](RECEIVABLES_ALLOCATION.md)를 통해 현재 양쪽 잔액을 부모 잠금 후 다시 검증한다. 오래된 suggestedAmount는 확정 권한이 아니다.

미인증 401, 타 tenant 경로 403, 타 tenant run ID/없는 ID는 같은 404 `MATCHING_RUN_NOT_FOUND`다. 입력 400 `INVALID_INPUT`, 소스 초과 422, DB 오류/저장 불변식 위반 503 `MATCHING_STORAGE_UNAVAILABLE`를 안전한 code/message JSON으로 반환한다. SQL·이름·금액·다른 tenant 자료를 오류에 노출하거나 로그에 남기지 않는다.

## 검증 범위

전용 `reconciliation_test`와 개발 DB 거부 initializer, 실제 security filter를 사용하는 통합 테스트 14개를 추가했다. 정확한 금액/동일 이름/부분 후보, 빈 자료와 애매한 입금, 기간 양끝·오래된 청구·날짜 경고, 완납 제외와 취소 복원, 잘못된 저장 잔액 fail-closed, 정렬/top 표시 상한, Unicode 증거 보존, source 상한과 잘못된 설정, 입력/권한/직접 서비스 세션 재확인, 저장 결과 재전송/병렬 키 충돌/rollback, 배분 후 snapshot 불변과 기존 확정 재검증을 검사한다. AI 키와 스위치가 설정되어도 AI client/query 호출은 0인지 확인한다.

V13 도입 후 테스트는 자기 tenant의 usage event/월 행 → V11 → V10 → V9 → identity 순서로 정리한다. 새 FK에 필요한 최소 matching cleanup 변경만 적용했다. 전체 회귀·패키징은 [작업 기록](AI_REVIEW_WORK.md)에 별도로 기록한다.

V13 한도 소진은429 `MATCHING_MONTHLY_LIMIT`와 usage/reset/Retry-After, 월 잠금 대기 중 월 변경은409 `MATCHING_MONTH_CHANGED`다. 실패 키 재시도와 과거/동일 키 0차감 재생 계약은 [사용량 문서](RECEIVABLES_USAGE.md)를 따른다.
