# 월별 성공 후보 실행 사용량 독립 검토

> 문서 범위: 사용량 기능 단계의 독립 검토 이력이다. 아래 “남은 단계”와 테스트 수치는 당시 기준이며 이후 완료 상태는 [현재 계획](RECEIVABLES_FREE_MVP_PLAN.md)을 따른다. 검증 주체와 근거는 원문대로 보존한다.

2026-11-01. `fe4a2c2` 이후의 matching 변경, `UsageClock/Policy/Service/Controller/Exception`, additive V13, 신규 통합 테스트와 [사용량 계약](RECEIVABLES_USAGE.md)을 검토했다. 검토자는 소스와 실행 산출물을 직접 읽었으며 DB 테스트·전체 빌드·HTTP 요청·유료 모델 호출을 중복 실행하지 않았다.

## 현재 판단

현재 작업본에서 미해결 actionable 결함을 발견하지 못했다. 초기 구현은 월 행 잠금 전에 시각과 plan을 확정해 잠금 대기 중 월/plan 변경을 놓칠 수 있었다. 최종 `UsageService.approve()`는 run/key 확보 후 월 행을 잠그고 DB 시각과 현재 인증·tenant plan을 다시 읽는다. 월이 바뀌면 `409 MATCHING_MONTH_CHANGED`로 전체 rollback하며 같은 키를 새 월에 재사용할 수 있다. 승인 후 완료가 월을 넘는 경우에는 승인 월을 유지한다. 외부 plan 인자를 받던 helper도 package-private `approve(UUID)`와 내부 Ticket으로 바뀌었다.

## 확인한 경계

- 기존 bounded 동기식 matching의 한 READ COMMITTED 트랜잭션에서 run/key → 월 usage 순서로 잠근다. SUCCESS snapshot·run 승인 월·고유 usage event·완료 counter가 함께 commit된다. 결과/event/counter 실패나 source 검증 실패는 임시 run·새 월 행·사용량·키를 함께 rollback한다. 별도 비동기 worker나 durable reservation이 없는 현재 실행 모델에 lease/fencing 회수를 요구하지 않는다.
- 서로 다른 새 키는 같은 tenant/month 행에서 직렬화한다. 신규 월 행도 unique key와 upsert 후 행 잠금으로 경합을 처리한다. 같은 키는 선행 transaction의 commit을 기다려 원래 hash와 저장 결과를 읽는다. 같은 payload 재전송은 quota 검사 전에 반환하고 다른 payload는 409다. 다음 달 replay와 V13 이전 SUCCESS도 추가 차감하지 않는다. 정상 0건 결과는 저장된 SUCCESS이므로 1회다.
- PostgreSQL `clock_timestamp()`와 고정 `Asia/Seoul`이 승인 월을 결정한다. 테스트는 전용 `UsageClock`만 대체하며 인증/세션 Clock을 바꾸지 않는다. 잠금 후 승인 시각·plan·policy version·limit은 event에 보존된다. 클라이언트 날짜 범위·시각·plan·임의 query month로 과금 월이나 한도를 선택할 수 없다.
- FREE 기본 10회는 서버 설정 가설이며 STANDARD/PRO 기본값은 0이다. 공개 tier 변경이나 결제 경로를 추가하지 않았다. 새 승인은 현재 server plan과 설정을 사용하고, downgrade 후 실제 completed가 현재 limit보다 커도 과거 성공을 삭제하지 않는다. remaining은 0 아래로 내려가지 않으며 신규 승인은 `completed < effectiveLimit`일 때만 허용된다. DB 내부 cap과 event의 실제 승인 limit을 구분한다.
- V13은 기존 migration을 수정하지 않는다. 완료 수 음수/저장 cap 초과와 run별 중복 event를 DB 제약으로 거절하고, composite FK가 다른 tenant의 run에 event를 연결하지 못하게 한다. 결과·event·counter의 의미적 일치는 현재 유일한 성공 writer의 같은 트랜잭션이 보장한다. 임의 SQL로 같은 tenant 자료를 수정하는 운영자까지 막는 trigger는 없다.
- 공개 matching/usage 진입점은 활성 세션과 tenant membership을 확인한다. GET usage는 현재 월만 읽고 월 행을 만들지 않는다. quota 소진 후에도 저장 결과 replay/GET/list, CSV import, 수동 배분·취소와 원문 증빙 조회는 가능하며 사용량을 늘리지 않는다. 429는 현재 사용량·다음 서울 월 시작 `resetAt`·양수 `Retry-After` 초를 제공하고 header와 body 값이 일치한다. DB 오류 응답은 SQL 없이 기존 안전한 503이다.
- 가격·AI/provider·legacy 금융 계산을 변경하지 않았다. 신규 사용량 경로는 모델이나 legacy 전역 금융 query를 호출하지 않으며, 테스트는 매번 해당 mock 무호출을 확인한다.

## 실행 근거

신규 `UsageIntegrationTest` 15개를 읽었다. 열 번째 성공/열한 번째 거부, 같은/다른 키 replay와 conflict, 마지막 슬롯 경합, 신규 월 행/동일 키 경합, 서울 월 경계와 승인 월 유지, 월 변경 전체 rollback 후 같은 키 재시도, 역사 run 무소급, validation/source 상한/잘못된 source balance와 snapshot/event/counter 저장 실패, live plan downgrade, HTTP/direct service 인증·철회·tenant 경계, 실제 multipart import와 배분·취소의 quota 독립성, schema 불변식과 정책 검증을 포함한다. 월 변경 회귀는 두 clock 값을 주입해 잠금 전후 분기를 결정적으로 검사하며 실제 시간을 기다리는 테스트는 아니다.

`/workspace/.local/run/usage-targeted.log`는 `BUILD SUCCESSFUL in 25s`다. 해당 targeted XML 5개는 usage 15개·기존 matching 14개·기존 조회 3개로 총 32개이며 실패/오류/skip 0이었다. 최종 `backend-java/build/test-results/test/TEST-*.xml` 21개를 직접 합산해 전체 180개와 usage 15개 모두 실패/오류/skip 0임을 확인했다. `/workspace/.local/run/usage-full-build.log`에는 `bootJar`, `jar`, `test`, `check`, `build`와 `BUILD SUCCESSFUL in 52s`, 8개 task 실행이 기록돼 있다.

실제 `/workspace/.local/run/usage-http-smoke.py`와 `usage-http-smoke.log`도 읽었다. 11개 scenario는 익명 401, 현재 서울 월 읽기, 잘못된 요청 0차감, 빈 SUCCESS 1차감, replay 0차감, key conflict, 429 header/body 및 미래 reset, 소진 후 저장 결과 조회/replay, CSV 입금 2건 import, 배분·취소와 source 증빙 보존, 다른 tenant 사용량 격리를 검증했다. HTTP app log는 `reconciliation_test`와 V13 사용을 기록하며 smoke log는 합성 tenant 2개의 정리 완료를 기록한다. 서버는 AI 비활성/빈 키로 실행했고 통합 테스트는 model/query mock 무호출을 검사했다. 실제 유료 요청 수를 별도 계측한 검증을 뜻하지 않는다.

`usage-dev-app.log`에서 `reconciliation_dev`의 V12 → V13 적용과 정상 시작을 확인했다. `usage-dev-before.txt`와 `usage-dev-after.txt`는 모두 `1|1|1|2`다. 이는 기존 금융 자료 개수 유지와 migration/startup의 근거이며 모든 기존 셀의 byte 동일성이나 개발 DB 기능 테스트를 뜻하지 않는다. 기능 HTTP 검증은 테스트 DB에서 실행됐다.

## 후속 범위

현재 구현은 동기식 성공 실행 quota까지다. 비동기 전환, 모바일 연결, 보존·삭제/누적 저장량/운영 요청 제어와 공개 출시 준비는 별도 단계다. 이번 검토는 공개 배포나 유료 서비스 활성화 승인이 아니다.
