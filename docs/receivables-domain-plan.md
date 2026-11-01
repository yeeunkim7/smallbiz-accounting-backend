# 미수금·입금누락 P0 도메인 및 DB 계획

2026-11-01 P0 분석 이력. 이 문서 작성 당시에는 코드·기존 migration·DB를 변경하지 않았다. 이후 계정·tenant·AI 차단 경계는 AUTH_FOUNDATION.md와 V8로 구현됐으므로 아래의 당시 무가드 분석은 현재 상태와 구분한다. 기준 HEAD는 `580adf5`; 미커밋 AI 구현을 현재 코드로 함께 확인하고 보존한다. Java 21 / Spring Boot / PostgreSQL / Flyway / Gradle를 유지한다. 기존 대사 API 계약과 `reconciliation_test` 격리를 유지한다.

## 먼저 결정할 사항

1. `business_event`는 현금예정 원본이며 청구서가 아니다. `bank_transaction`은 입출금 원본이며 확정할당이 아니다. `daily_review`는 하루 검토 상태이며 개별 수금확정이 아니다. 세 테이블과 기존 계산의 의미를 바꾸지 않는다.
2. 새 `receivables` 경계에서 **청구 → 입금 → 규칙후보 → 사용자 확정 → 잔액/연체**를 구현한다. 후보 생성은 금액을 이동시키지 않는다. 부분입금과 묶음입금은 동일한 다대다 할당 모델로 처리한다.
3. FREE를 AI 호출 0회로 먼저 출시한다. 기존 AI HTTP POST도 서버에서 차단해야 한다. STANDARD/PRO는 후속이며 가격 9,900원/30,000원, FREE 월 10회는 검증 전 설정 가설이다. 가격·횟수·기능을 코드 상수로 확정하지 않는다.
4. 기존 테이블에는 tenant 소유자가 없다. 전역 원본 ID나 vendor ID를 사용자가 제시했다는 이유로 새 tenant 데이터에 연결하지 않는다. tenant 소유권을 입증하지 못한 기존 데이터는 연결·노출하지 않는 것이 P0 기본값이다.
5. 무료 횟수는 [총괄 계획 3절](RECEIVABLES_FREE_MVP_PLAN.md#3-등급한도-설정-가능한-가설)의 **선택 범위 규칙후보 생성 run이 정상 완료되고 결과가 저장될 때 1회** 가설을 따른다. 수동 확정/변경/취소, 조회·새로고침, import는 0차감이다. 입금 최초확정 여부는 사용량 단위가 아니다. 같은 완료 run 재요청은 저장된 결과를 반환하며 추가 차감하지 않는다.

## 실제 코드가 보여주는 재사용 경계

경로는 저장소 기준이다. 기존 흐름 전체 설명은 [ai-review-code-analysis.md](ai-review-code-analysis.md)의 Existing flows / Business semantics를 재사용한다.

| 근거 | 기존 의미와 한계 | 새 도메인에서의 처리 |
| --- | --- | --- |
| `backend-java/src/main/resources/db/migration/V2__create_business_upload.sql` | `business_event`: vendor, event_type, expected_cash_date/usage_date, amount. 전역 source_line_id 유니크. 청구번호·만기·미수잔액 없음 | 청구서로 변환하거나 expected_cash_date를 지급기한으로 재명명하지 않는다. 명시적 사용자 검증을 거친 별도 청구 입력만 허용 |
| `.../V4__create_bank_upload.sql`, `V7__bank_woori_statement_fields.sql` | 은행 IN/OUT, booked_date/at, 금액, 잔액, 원본 행. 전역 source_line_id와 upload/row 유니크 | IN 원본만 수금 증빙으로 연결. 원본 불변 유지; OUT을 음수 입금으로 자동 치환하지 않는다 |
| `.../V6__create_daily_review.sql`, `reconciliation/DailyReviewService.java` | review_date 단일 PK, 검토버전, 원본 변경 시 재검토. 개별 청구/입금 FK 없음 | 패턴(트랜잭션·락·버전)만 참고. 새 확정이 daily_review를 완료시키지 않음 |
| `reconciliation/ReconciliationAggregationRepository.java` | 업무/은행을 날짜별 각각 합산한 뒤 조인. IN=CHARGE_EXPECTED+SETTLEMENT_EXPECTED, OUT=REFUND_EXPECTED; USAGE 제외 | 날짜 합계일치는 거래별 수금확정이 아니다. 새 잔액은 allocation으로 계산. 기존 집계 쿼리를 미수금 조회에 재사용하지 않음 |
| `.../V1__create_vendor.sql`, `vendor/VendorService.java` | vendor_code 전역 유니크, PREPAID/POSTPAID. findAll/findById에 tenant 없음 | 이름/코드 검증·UI 패턴 참고. 새 tenant 거래처를 만들고 필요한 경우 검증된 legacy mapping만 보유. 기존 vendor를 tenant 공동마스터로 직접 사용하지 않음 |
| `upload/BankUploadService.java`, `BankTransactionFingerprint.java` | 파일해시/원본ID 중복방지, W1은 일시·방향·금액·잔액 해시. 저장 시 기존 daily_review 변경 | parser/정규화/해시 유틸 재사용 검토. 기존 save 호출은 전역 원본 저장·일일검토 부작용 때문에 새 tenant import에 그대로 사용하지 않음. W1은 계좌 식별이 없어 tenant/계좌 밖 동일거래 증명 불가 |

기존 일별 500,000+800,000과 400,000+900,000의 합계일치는 새 청구 두 건이 모두 수금되었다는 증거가 아니다. 또 지급기한 이전 청구도 미수잔액은 존재한다. 미수금은 날짜별 현금 차이가 아니라 **개별 채권의 금액 중 확인된 수금이 아직 할당되지 않은 금액**이다.

## 최소 논리 모델 (향후 additive migration의 입력)

모든 새 업무 테이블은 `tenant_id`, 불변 ID, 생성시각을 가진다. tenant 및 인증 사용자/멤버십은 새 서비스의 선행 기반이며 브라우저 전달 tenantId를 인증으로 취급하지 않는다. 이름은 논리명이며 이 문서에서 DDL을 확정하거나 적용하지 않는다.

| 모델 | P0 최소 필드·제약과 필요성 |
| --- | --- |
| tenant / membership | tenant, user, role, active. 서버에서 선택 tenant의 활성 멤버 확인. 조회 역할과 청구/입금/확정 권한 구분 |
| receivable_counterparty | tenant, code, display_name, normalized_aliases, version. `(tenant, code)` 유니크. 거래처명 동일만으로 자동 동일인 확정 금지 |
| invoice | tenant, counterparty_id, invoice_number(tenant 내 유니크), issued_date, due_date, amount, currency, lifecycle, version. P0 KRW 정수 원. due_date는 필수. 청구 금액·기한·거래처 변경 감사; 할당이 있으면 금액변경은 취소/정정 경로만 허용 |
| payment | tenant, received_date/at, amount, currency, payer_name, account/source identity, import_batch_id?, external_source_key?, lifecycle, version. 수동입금도 허용하되 근거 및 idempotency 필요. 원본 내용과 수금 입력의 출처 구분 |
| import_batch | tenant, source_kind, account_scope, content_sha256, parser_version, status, accepted/rejected counts, created_by. 같은 tenant·계좌·형식의 파일 재시도는 같은 결과로 반환. 파일명은 식별키가 아님 |
| payment_allocation | tenant, payment_id, invoice_id, positive amount, confirmation_id, reversed_by/reversed_at. 활성 할당 합계만 잔액에 반영. 변경은 기존 행 덮어쓰기 대신 reversal+신규행을 한 트랜잭션으로 기록 |
| confirmation_command | tenant, idempotency_key, canonical payload hash, actor, operation, status/result, created_at. `(tenant, operation, key)` 유니크. 결과 유실 재시도·동시요청에서 동일 결과 보장; 결과에 할당 및 감사 ID 저장 |
| reconciliation_audit | tenant, actor, event_type, aggregate IDs/versions, before/after 또는 추가/반전 행 ID, reason, command_id, occurred_at. 확정·변경·취소·원본보정·거래처/기한변경. 앱 계정의 임의 UPDATE/DELETE를 허용하지 않음 |
| candidate_run / candidate_result | tenant, operation, idempotency_key, canonical payload hash, 선택 범위·대상 version, rule_version, status, 승인 business_month, policy_version, lease/fencing token, 결과. `(tenant, operation, key)` 유니크. 정상 완료 결과를 영구 저장해 중복 요청에 재사용 |
| tenant_month_usage / usage_event | tenant, business_month, metric, success_count, reserved_count, policy_version. event는 tenant/run/metric 유니크와 승인 월. 정상 완료 run별 성공 증빙; payment 또는 allocation과 과금 연결하지 않음 |
| source_ownership / legacy_source_link | 기존 원본을 연결할 때만 필요. 검증된 tenant+account+upload 소유권 및 감사, tenant payment↔legacy bank ID 일대일. P0 신규 tenant 업로드만 제공하면 legacy 연결은 보류 가능 |

후보 run과 완료 결과는 사용량·재시도 정합성을 위해 영구 저장한다. 결과는 입금/청구 ID, 제안 할당금액, 금액일치/이름/기한 근거, 규칙버전, 대상 version을 포함한다. 같은 완료 run 조회는 결과를 재생성하거나 차감하지 않는다. 후보 자체는 회계 사실이 아니며 확정 시 현재 잔액·version을 다시 검증한다. 과거 추천 이유는 사용자 확정 audit에 남긴다. 후보가 0건이어도 선택 범위의 규칙처리가 정상 완료되어 결과가 저장됐다면 성공 run 1회다.

규칙은 동일 tenant·통화·활성 청구/입금의 가용잔액만 사용한다. 금액·거래처 별칭·일자범위로 정렬하고 애매하면 복수 후보/미해결을 반환한다. 규칙의 대상 수·기간·계산량은 설정 상한으로 제한한다. 복잡한 자동 부분집합·묶음 조합 탐색은 후순위이며 묶음 배분은 사용자가 직접 지정할 수 있다. 부분입금과 합계일치만으로 자동확정하지 않는다.

## 금액 불변식과 변경 정책

- `amount > 0`, KRW 단일 통화, 저장/계산은 정수 원. 기존 원본 상한과 호환되는 입력한도를 설정으로 정하고, 합산은 overflow 검증(BigInteger 또는 검증된 안전 범위). 프런트/AI의 합계는 권위가 없다.
- 각 invoice의 `outstanding = invoice.amount - SUM(active allocations)`는 `0..invoice.amount`.
- 각 payment의 `unallocated = payment.amount - SUM(active allocations)`는 `0..payment.amount`. 초과입금은 잔여 미할당으로 남고 청구잔액을 음수로 만들지 않는다.
- 할당은 같은 tenant·통화의 활성 invoice/payment 사이 양수여야 한다. `(tenant_id,id)` 복합 unique 및 복합 FK로 tenant가 다른 거래처/청구/입금 연결을 DB에서도 차단한다. 단순 FK(id)만으로는 부족하다.
- 부분입금: 청구 100, 입금 40 → 할당 40, 미수 60. 이후 다른 입금 60으로 완납. 묶음입금: 입금 150 → 청구 A100+B50. 여러 payment→여러 invoice도 같은 행 모델이며 전체 명령을 all-or-nothing으로 확정한다.
- `UNPAID/PARTIAL/PAID`는 활성 할당 합계에서 파생한다. 연체는 tenant 업무 timezone 기준 `due_date < today && outstanding > 0 && invoice active`; 기한 당일은 연체 아님. 입금누락 표현은 미확인/미할당 상태를 설명하며 실제 은행 미입금을 단정하지 않는다.
- 확정 변경/취소는 사유 필수, 이전 command와 연결하고 actor/time을 남긴다. 이미 반전된 행을 다시 취소하면 같은 성공 결과 또는 명시 충돌을 반환. 취소로 미수/연체가 다시 나타난다. 결제환불·대손·세금계산서·자동독촉은 P0 밖이다.
- 할당된 청구/입금의 하드삭제 금지. 취소된 청구의 할당은 동일 트랜잭션으로 반전하거나 취소요청을 거절한다. 입금 원금/청구 원금 변경은 먼저 관련 할당을 명시 정정하며 조용히 잔액을 덮어쓰지 않는다.

## 확정·취소 트랜잭션 및 lock 순서

PostgreSQL READ COMMITTED + 명시 행잠금을 기본으로 한다. SUM 제약은 일반 CHECK만으로 보장할 수 없으므로 모든 allocation 쓰기를 단일 서비스 경로로 모으고, payment/invoice 부모를 잠근 뒤 현재 합계를 재검사한다. 다른 writer가 추가될 때 같은 프로토콜 준수 또는 DB 강제방식을 재검토한다. **배분 정합성 트랜잭션은 후보 run 사용량 트랜잭션과 분리하며 월 quota가 소진돼도 수동확정·취소를 막지 않는다.**

1. 인증·tenant 멤버십·권한·기능 entitlement 검증. actor는 서버 세션에서 결정한다. 재전송도 현재 권한을 통과해야 결과를 보여준다.
2. `(tenant, operation, idempotency_key)` 명령행을 insert-on-conflict로 확보하고 잠근다. 같은 키+같은 canonical payload이면 기존 결과 반환, 다른 payload면 409. 동시 insert 충돌은 선행 commit을 기다린 뒤 결과를 읽는다. 실패로 전체 rollback된 명령은 재시도 가능하다.
3. 영향을 받는 payment 전부를 ID 오름차순으로 잠근 뒤 invoice 전부를 ID 오름차순으로 잠근다. 기존 할당을 변경하면 기존/신규 대상의 합집합을 먼저 잠근다. 대상집합이 사전조회 이후 변했으면 버전을 재검증하고 충돌로 종료한다. allocation은 부모락 이후에 읽고 수정한다. 모든 confirm/change/cancel 경로가 같은 순서여야 한다. 이 경로에는 quota 잠금·usage_event 쓰기가 없다.
4. 요청 version, tenant 소유권, 활성상태, 통화, 현재 가용잔액, 반전 여부를 DB에서 재검증. 후보의 오래된 금액을 믿지 않는다. 현행 활성할당을 제외/반전한 최종 상태로 양쪽 합계 불변식을 확인한다.
5. 할당/reversal, audit, command 결과를 같은 트랜잭션으로 commit한다. 응답 유실 재시도는 기존 결과를 반환한다. 실패 시 전부 rollback하고 확정·변경·취소의 사용량 차감은 항상 0이다.

## 규칙후보 run의 성공 사용량·예약·장애 복구

월 10회는 설정 가능한 가설이다. Java 메모리 Semaphore나 별도 비원자 counter로 제어하지 않으며, 멀티 인스턴스에서도 DB가 기준이다. 작업승인 DB 시각을 사업체 시간대(초기 Asia/Seoul)로 변환한 business_month와 policy version을 run에 고정한다. 기기 시계·완료시각·재요청 날짜로 월을 바꾸지 않는다.

1. **승인 트랜잭션:** 인증·권한·run 요청 검증 후 `(tenant, operation, Idempotency-Key)`와 canonical payload hash로 run을 확보한다. 같은 키/다른 payload는 409, 완료된 같은 요청은 결과 반환, 실행 중인 같은 요청은 같은 run 상태를 반환한다. 다른 키의 명시적 새 실행은 별개 run이다. 요청 범위가 같다고 완료 run을 임의 덮어쓰지 않는다.
2. 공통 잠금 순서는 **run 행 → 해당 월 usage 행**이다. usage row는 unique+upsert로 생성한 뒤 잠근다. `success_count + reserved_count < configured_limit`일 때만 예약을 하나 늘리고 승인 월·lease deadline·단조증가 fencing token을 run에 저장한다. 예약 후에는 `success_count + reserved_count <= limit`을 유지한다. 승인실패는 예약 0·성공차감 0이다.
3. worker는 tenant 범위 내 제한된 규칙계산만 수행한다. provider/AI 호출은 없다. lease 갱신·재시작은 현재 token을 조건으로 하며 재시작할 때 token을 증가시킨다. 이 계산은 payment/invoice에 할당을 쓰거나 긴 부모락을 잡지 않는다.
4. **성공 트랜잭션:** 같은 run→월 usage 순서로 잠그고 현재 token·실행상태·유효 lease를 검사한다. candidate_result 저장, run SUCCESS, 해당 승인 월 reserved_count −1 및 success_count +1, run별 유니크 usage_event를 한 번에 commit한다. 결과 저장 실패는 성공으로 차감하지 않는다. 결과의 대상 version은 수동확정에서 다시 검사한다.
5. **실패/회수 트랜잭션:** 동일 잠금 순서에서 현재 token·상태를 조건으로 FAILED/EXPIRED 처리하고 예약을 한 번만 반환한다. 성공수는 증가시키지 않는다. lease가 만료된 worker의 늦은 성공은 token/상태 조건으로 거절한다. 예약회수 후 재시도는 같은 run의 새 token으로 한도를 재확인·예약한다. 완료된 run은 재예약하지 않는다.
6. 월말을 넘어 완료하거나 같은 키로 재시도해도 최초 승인 월을 유지한다. 회수 후 같은 run을 재승인하는 경우에도 그 월과 정책을 유지한다. 갱신 정책이 이미 존재하는 예약·완료 결과를 소급 변경하지 않도록 한다.

후보 run의 실패·미승인·quota초과는 성공 차감 0이며 임시 예약만 회수한다. import 실패·중복, 수동확정/취소, 조회·저장된 결과 새로고침은 이 metric을 변경하지 않는다. allocation audit와 run 성공 usage_event는 서로 다른 사실이므로 한 쪽의 성공을 다른 쪽으로 추정하지 않는다.

## 업로드 중복·기존 원본 연결·tenant 보안

- 신규 업로드는 tenant 전용 수금 import 경로로 저장한다. 파일 단위 `(tenant, account_scope, source_kind, content_hash)` 유니크, 거래 단위 `(tenant, account_scope, source_kind, external_source_key)` 유니크로 동일파일·겹치는 기간 파일·동시 import를 방어한다. DB unique가 최종 방어이며 사전 exists만으로 처리하지 않는다. parser 버전이 달라도 같은 원천거래를 새 거래로 생성하지 않는다.
- 계좌/기관이 안정적인 거래ID를 제공하면 우선 사용한다. 그렇지 않으면 일시/방향/금액/잔액 등을 사용하되 충돌을 검토상태로 반환한다. 동일 금액·날짜·이름만으로 실제 별개 입금을 삭제하지 않는다. 파일은 원본 증거이고 payment는 canonical 수금이므로 여러 import에서 같은 payment로 이어지는 provenance를 보존한다(별도 import-row/link는 이 기능이 있으면 필요).
- 기존 upload_file의 SHA나 bank source_line_id는 전역 유니크다. 다른 tenant와의 중복충돌 응답으로 자료 존재가 누출될 수 있어 기존 upload facade를 tenant endpoint 뒤에 단순 래핑하지 않는다.
- 기존 원본은 소유자 미상이다. 관리자가 출처·계좌 소유권을 검증해 승인한 source_ownership만 연결 가능하고, 선택한 bank ID가 그 소유 upload에 속하는지 서버에서 확인한다. 승인 이력 없이 “기존 데이터는 첫 가입자 소유”로 할당하지 않는다. 원본은 그대로 두고 새 payment snapshot+link 생성; 이후 원본과 불일치하면 경고/감사로 처리한다.
- 신규 tenant API는 list/detail/search/export/candidate/confirm/audit/import 전부 tenant 조건을 가진다. 다른 tenant ID 추측은 결과·파일명·건수도 노출하지 않는다. source_link와 legacy detail를 조회할 때도 소유권 조인을 강제한다. 기존 전역 조회·vendor·AI query API를 로그인 사용자에게 그대로 개방하면 우회가 되므로 신규 서비스 배포에서 legacy 경로는 별도 내부접근으로 격리하거나 비활성화한다.
- vendor는 이름만 복사해 tenant 거래처로 만들 수 있지만 legacy vendor_id 연결은 소유 upload/business_event까지 검증되어야 한다. PREPAID/POSTPAID가 새 채권 상태를 결정하지 않는다.

## FREE AI 0콜 및 후속 PRO 가드

현재 `aireview/ai/AiReviewController.java`의 `POST /api/v1/ai-review/reviews`는 `service.review(request.date())`를 바로 호출한다. `AiReviewService.review`가 확인하는 것은 client configured 및 프로세스 내 Semaphore(2)뿐이다. `backend-java/build.gradle`에는 Spring Security 의존성이 없고 현재 main 소스에는 tenant/plan/entitlement 인증가드가 없다. 최대 5 model requests/검토, timeout, read-only 도구와 mock 테스트는 비용상한/도구경계이며 유료권한 가드를 대신하지 않는다. API key를 설정하면 호출 가능해지는 현행 경로가 FREE 서비스의 출시 차단점이다.

- FREE P0는 서버 기본 `AI_ENABLED=false`인 global kill-switch와 route/service 공통 차단으로 model client에 도달하지 못하게 한다. key 존재 여부·UI 숨김만으로 허용하지 않는다. 기존 AI POST alias/후속 endpoint도 동일 가드를 거친다. 신규 receivable 추천은 규칙만 사용한다.
- 후속 PRO에서만 인증→tenant membership→서버 저장 entitlement/유효기간→AI feature flag→허용량/비용 budget을 확인한다. FREE/STANDARD/미확인 등급/설정장애/만료 시 fail closed. 요청 JSON의 plan/pro/price는 신뢰하지 않는다. capabilities는 현재 사용자의 실제 권한을 나타내되 비밀이나 다른 tenant 정보를 노출하지 않는다.
- kill-switch는 모든 인스턴스가 신속히 재확인할 수 있게 하고 요청 진입 및 각 provider 호출 직전에 확인한다. 이미 전송된 외부 요청은 되돌릴 수 없음을 운영 지표에 남긴다. 최대 5회에는 retry도 포함, 무한재시도 금지; 자동검증은 mock만 사용한다.
- AI는 새 tenant 읽기 allowlist만 사용하고 청구/입금/확정/완료·quota 쓰기를 실행하지 않는다. 현재 global ReadOnlyReviewQueries를 신규 tenant AI에 그대로 제공하지 않는다. 모델 출력·메모·파일명은 비신뢰 데이터; 실제 금액/잔액 및 성공 여부는 Java/DB가 결정한다.
- 요금 9,900/30,000원과 무료 10회는 버전 있는 plan policy 설정과 표시문구의 동일 출처를 둔다. 가격 설정이 곧 과금 구현이나 가입 가능 상태를 뜻하지 않는다. STANDARD/PRO 판매·결제 개발은 P0 범위 밖이다.

## 이번 P0와 후속 MVP 구현·검증 계획

총괄 계획과 같은 후속 구현 순서는 (1) 인증·사업체 소유권·entitlement·AI0 가드, (2) 청구/입금 직접등록·원본 모델/읽기 API, (3) 수동 allocation/취소·audit·미수/연체, (4) 규칙후보 run·tenant import·성공 사용량, (5) Expo 핵심 화면이다. 기존 Flyway V1–V7 수정 금지; additive migration은 실제 구현에서 따로 리뷰한다.

필수 검증은 `reconciliation_test`에서 기존 격리가드 유지 후 수행한다. 이번 문서 작업에서 DB/자동테스트/실모델 호출은 실행하지 않았다.

| 검증 | 기대 결과 |
| --- | --- |
| 부분 40+60, 입금150→청구100+50, 다대다, 초과입금 잔액, 정확한 만기경계 | 양쪽 잔액 불변식, 연체/완납 파생값 정확 |
| 동일 payment 동시확정, 동일 invoice 동시확정, 반대 순서 다대다 요청 | 과할당 없음; 순서락 또는 명시적 version 충돌; deadlock 발생 시 bounded 재시도 |
| 수동확정 같은 키·같은 payload 동시 retry/응답유실, 다른 payload 재사용 | 동일 결과 또는 409; 할당·audit 중복 없음; quota 변화 0 |
| 월한도 마지막 1칸 서로 다른 run 동시승인, 신규 usage row 동시생성 | success+reserved 한도 유지; 한 run만 예약 가능 |
| 같은 run 동시 retry/응답유실, 같은 키 다른 payload, 정상 0건 후보결과 | 결과·usage_event·성공수 1회 또는 409; 결과 저장된 정상 run은 1회 |
| worker 중단·lease만료·예약회수·늦은 worker 완료·새 token 재실행 | 예약 1회 반환, 낡은 token 성공 거부, 중복 완료/영구 예약 누수 없음 |
| 월말 승인→다음달 완료/재시도, 실패 후 같은 run 재예약 | 최초 승인 business_month와 policy 유지, 다른 월 counter 오염 없음 |
| run 결과/usage 저장 각 지점 rollback, 저장된 후보 조회, import 실패/중복 | 성공 결과·counter·event 원자성; 실패 성공차감 0; 조회/import 차감 0 |
| 수동확정 stale 후보/권한실패 및 allocation/audit 저장 각 지점 rollback | 배분 불변식 유지, audit/command 원자성; quota 경로 호출 없음 |
| 확정취소→재확정/다음달 재확정, 변경 시 기존 대상+신규대상 동시 충돌, quota 소진 후 수동확정 | 반전이력·원금 보존; 모든 수동배분 차감 0; quota 소진이 배분을 차단하지 않음 |
| 같은 파일 retry, 서로 다른 파일 내 동일거래, 다른 계좌 동일 W1유사행 | canonical payment 중복 없음; 별개 계좌 입금은 유지; 불확실 충돌은 검토 |
| tenant A로 B의 invoice/payment/vendor/source/upload/후보/감사 ID 요청 | 조회·연결·수정·존재정보 누출 차단, 복합 FK 교차연결 거절 |
| legacy 소유권 미확인·잘못된 link, 기존 전역 API 직접접근 | 연결/조회 차단; 기존 대사 의미·계산·API 회귀 없음 |
| provider key가 설정된 mock 상태에서 FREE/STANDARD/무인증/flag off가 기존 AI POST 직접 호출 | 외부 client 호출 횟수 0; 후속 PRO+유효권한+flag on만 경계통과 |

이번 P0 완료 판단은 기존 작업 보존, 코드/스키마 점검, 설계·위험·미확인 사항 기록, 실제 수행한 기존 회귀검증의 증거 확보다. 이 문서 작성 자체는 신규 정합성 테스트 완료를 뜻하지 않는다. 무료 end-to-end 수금확정·동시성/격리 검증·Expo 실제 동작은 후속 MVP 출시 게이트다. 실제 AI 유료호출, 대량 migration, 운영배포도 이번 P0 완료로 표시하지 않는다.
