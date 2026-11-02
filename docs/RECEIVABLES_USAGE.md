# 월별 성공 후보 실행 사용량

V13은 기존 동기식 matching run에 월별 성공 한도를 결합한다. 기존 V1~V12 migration과 금융 원본은 보존한다. 공개 FREE 출시는 아직 완료되지 않았으며 모바일·보존/삭제·운영 준비는 후속 범위다.

## 실행과 원자성

사용자가 새 키로 선택 범위의 규칙 후보를 생성해 `SUCCESS` 결과가 저장되면 1회다. 자료/후보가 0건인 정상 완료도 1회이며, 이는 파일 누락이나 실제 입금 확인을 의미하지 않는다. 조회·CSV 가져오기·수동 배분/취소는 0회이며 한도가 소진돼도 계속 사용할 수 있다. AI 호출은 없다.

기존 bounded 동기식 처리의 **한 READ COMMITTED 트랜잭션**을 유지한다. run/key 확보 → 월 사용량 upsert/행 잠금 → 현재 권한·서버 plan/정책 및 한도 확인 → 하나의 SQL snapshot으로 금융 소스 읽기 → SUCCESS 결과·run 승인 월·고유 usage event·완료 counter를 함께 commit한다. 잠금 순서는 run → 월 usage다. 서로 다른 새 실행은 같은 월 행에서 직렬화하며 같은 키는 선행 run의 commit을 기다린다. 계산량 상한은 [후보 계약](RECEIVABLES_MATCHING.md)을 유지한다.

실패하면 임시 run·새 월 행·결과·event·counter·키를 모두 rollback한다. 결과/event/counter 저장 중 오류도 0차감이며 같은 키로 재시도할 수 있다. DB 제약은 완료 수의 음수/저장 cap 초과, run별 중복 event, 다른 tenant run 연결을 거부한다. event에는 승인 시 plan·policy version·limit·DB 시각을 보존한다. 애플리케이션의 유일한 성공 writer가 결과/event/counter를 같은 트랜잭션으로 기록한다.

이는 이전 계획의 비동기 예약·worker·lease/fencing 제안을 **현재 bounded 동기식 구현에 맞춰 대체**한다. durable reservation이나 별도 worker를 추가하지 않았다. 프로세스/연결 중단 시 PostgreSQL이 미완료 트랜잭션을 rollback하므로 회수할 영구 예약은 없다. 비동기 전환은 별도 설계/검증 대상이다.

## 월과 재전송

기준 시간대는 고정 `Asia/Seoul`이다. 서버가 PostgreSQL `clock_timestamp()`를 읽어 월 첫날을 선택하고 월 행 잠금 뒤 DB 시각을 다시 읽는다. 잠금 대기 중 월이 바뀌면 전체 rollback 후 409 `MATCHING_MONTH_CHANGED`를 반환한다. 같은 키로 다시 실행할 수 있다. 잠금 뒤 확인한 DB 승인 시각의 월을 run과 event에 고정하며, 그 뒤 월을 넘어 완료해도 승인 월에 기록한다. 기기 시각·요청 from/to·query month를 사용하지 않는다. `UsageClock`은 PostgreSQL을 기본으로 하는 테스트 seam이며 인증/세션 시계를 대체하지 않는다.

같은 tenant/key와 같은 payload는 원래 결과 그대로 반환하고 한도를 다시 차감하지 않는다. 다른 payload는 quota 검사보다 먼저 409 `IDEMPOTENCY_CONFLICT`다. 다음 달에 재전송해도 원래 run/월을 유지한다. V13 이전 SUCCESS는 그대로 재생하며 소급 차감하지 않는다. quota 거부는 실패 run이나 사용한 키를 남기지 않는다.

## 서버 정책

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `app.receivables.usage.free-limit` | 10 | 검증할 FREE 월 한도 가설 |
| `app.receivables.usage.standard-limit` | 0 | 명시적 운영 설정 전 신규 실행 거부 |
| `app.receivables.usage.pro-limit` | 0 | 명시적 운영 설정 전 신규 실행 거부 |
| `app.receivables.usage.policy-version` | `matching-usage-v1` | 1~80자 ASCII 영문/숫자/`._:-` |

한도는 0 이상의 int다. 잘못된 설정은 시작 시 거부한다. 가격·결제·구독 entitlement를 구현하거나 유료 기본 한도를 약속하지 않는다. 서버의 현재 tenant plan과 현재 정책으로 새 실행을 승인하며 클라이언트가 plan/한도/월을 정할 수 없다. 운영자가 중간에 plan/한도를 낮춰도 기존 성공은 삭제하지 않는다. 따라서 조회의 `completed`가 현재 유효 `limit`보다 클 수 있으며 `remaining=max(0,limit-completed)`다. 이 경우 신규 실행은 거부하고 원래 결과 재전송/조회는 허용한다. DB 내부 cap은 이미 완료된 수를 보존하고 현재 admission 한도는 별도로 검증한다.

## API

`GET /api/v1/tenants/{tenantId}/matching-usage`는 Bearer 인증·현재 활성 세션·membership을 검증하고 현재 월만 반환한다. query parameter는 허용하지 않는다. GET은 월 행도 생성하지 않으며 완료 수를 늘리지 않는다. 현재 자료가 없으면 `completed:0`이다. 직접 서비스 호출도 현재 권한을 다시 확인한다. 미인증/철회 세션 401, 타 tenant/철회 membership 403이다.

```json
{"month":"2026-11","timezone":"Asia/Seoul","plan":"FREE","policyVersion":"matching-usage-v1","limit":10,"completed":3,"remaining":7,"resetAt":"2026-11-30T15:00:00Z"}
```

`POST /matching-runs` 한도 소진은 429다. 다음 형식으로 안전한 오류와 표시 가능한 사용량을 반환하며 `Retry-After` 초 헤더도 제공한다. `resetAt`은 다음 서울 월의 시작을 UTC instant로 표현한다. 다른 tenant/SQL/금융 정보는 오류에 포함하지 않는다.

```json
{"code":"MATCHING_MONTHLY_LIMIT","message":"The monthly successful matching-run limit has been reached.","usage":{"month":"2026-11","timezone":"Asia/Seoul","plan":"FREE","policyVersion":"matching-usage-v1","limit":10,"completed":10,"remaining":0,"resetAt":"2026-11-30T15:00:00Z"},"retryAfterSeconds":2592000}
```

retry 시간은 응답 시점의 서버 DB 승인 시각과 reset 차이에 따라 달라진다. 한도 0인 유료 기본 정책은 다음 달에도 설정이 바뀌기 전까지 신규 실행을 허용하지 않는다. DB 오류는 기존 503 `MATCHING_STORAGE_UNAVAILABLE`로 안전하게 반환한다.

## 검증

전용 `reconciliation_test`의 실제 PostgreSQL과 보안 필터로 월 경계·동시성·원자성·인증을 검증한다. 테스트는 자기 tenant의 V13 event/월 → V11 run → V12/V10/V9 → identity 순으로 정리하며 기존 matching 테스트도 새 FK의 자기 사용량 행만 먼저 정리한다. 테스트 모델/query mock 호출은 0이며 실제 유료 API는 호출하지 않는다. 최종 검증 결과는 [작업 기록](AI_REVIEW_WORK.md)과 [HTTP 검증](RECEIVABLES_USAGE_SMOKE.md)에 기록한다.
