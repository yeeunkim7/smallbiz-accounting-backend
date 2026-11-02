# Tenant 은행 CSV 가져오기 독립 검토

2026-11-01. 검토 대상은 새 `receivables.bankimport` package, additive V12, malformed multipart 공통 오류 처리, 신규 통합 테스트와 [API 계약](RECEIVABLES_IMPORT.md)이다. 기존 등록·배분 경계는 연동에 필요한 범위에서 읽었다. 검토자는 소스와 실행 산출물을 읽었으며 DB 테스트·전체 빌드·유료 모델 호출을 중복 실행하지 않았다.

## 현재 판단

현재 소스에서 미해결 actionable 결함을 발견하지 못했다. 초기 구현의 수동 등록 `strip()`/Unicode code-point 규칙과 legacy parser의 `trim()`/UTF-16 길이 규칙 차이는 최종 구현에서 제거됐다. 한 번의 엄격한 CSV parse와 기존 `ReceivablesValidation`으로 정규화 값을 구성하며, 100개/1000개 astral 문자와 Unicode 공백·원문 보존 회귀 case를 확인했다. 기존 allocation 취소 계약에 없는 `expectedVersion`을 보내던 신규 테스트도 `reason`만 보내도록 수정됐다.

## 확인한 경계

- 모든 공개 service entry가 현재 세션과 tenant membership을 확인한다. SQL 조회·재전송·정체성 검색은 tenant 범위이며, V12 composite FK가 import/source/transaction/payment의 다른 tenant 연결을 막는다. `MANUAL`은 bank source가 null, `BANK_CSV`는 non-null이어야 한다. transaction의 IN/payment 존재, OUT/payment 부재와 import 집계 합계도 DB 제약이다. 같은 tenant 안에서 source·transaction·payment가 서로 올바른 최초 관측을 가리키는 의미적 일치는 service 삽입 경로가 보장하며, 임의의 같은-tenant SQL 변경까지 막는 trigger는 없다. 공개 변경 경로는 발견하지 못했다.
- 거래 정체성은 `(tenant,bankAccountKey,source_line_id)`다. 날짜·금액·이름이 같은 별도 ID를 합치지 않는다. 동일 ID의 정규화 날짜·방향·금액·이름·메모 변경은 전체 409 rollback이다. 계좌 alias와 원천 ID의 실제 은행 소유권·안정성을 서버가 증명하지 않는 한계가 계약에 명시돼 있다. Woori 7-column 형식과 `W1.<64 lowercase hex>` 지문은 거절한다.
- 엄격한 UTF-8과 선택적 선두 BOM, 정확한 6-column header, RFC4180 quote, 정수 KRW·실재 날짜·방향·텍스트 검증을 적용한다. 잘못된 encoding/CSV·중복 ID·빈 레코드는 전체 실패다. 실제 stream을 기본 1MiB+1까지만 읽고 기본 1000행을 초과하면 거절한다. 설정은 최대 5MiB/10000행 범위로 제한되며 원문 증빙 전체도 파일 상한 안에 있다. 목록은 최대 100개, offset은 long이다.
- 검증 후 하나의 트랜잭션이 tenant row를 잠그고 재인증한 뒤 import·원천 관측·거래·payment·재전송 key를 함께 저장한다. tenant 단위 직렬화로 같은 key/file 및 역순 overlap 경합을 순서대로 처리한다. 기존 payment나 allocation 원금을 수정하지 않으며 OUT은 payment를 만들지 않는다. 기존 allocation은 payment→invoice 순으로 기존 부모를 잠그지만 import는 기존 부모 UPDATE lock을 요구하지 않아 현재 코드에서 반대 순서의 lock cycle을 발견하지 못했다. allocation의 tenant FK lock 대기는 가능한 처리량 제약이다.
- 같은 key의 같은 account/file hash는 저장 결과를 반환한다. 새 key의 같은 account/file도 같은 import를 반환하고 새 key를 원자적으로 기록한다. filename은 요청 의미에서 제외된다. 다른 파일의 overlap은 각 파일의 source cells를 남기고 같은 payment에 연결한다. 재전송 판단 전에 파일·filename을 현재 규칙으로 재검증하므로, 운영자가 나중에 상한을 낮추면 과거 성공 파일의 POST replay도 거절될 수 있다. 저장된 GET 조회에는 이 파일 재검증이 없다.
- `originalCells`는 정확한 6개 parsed string의 JSON 배열이다. CSV quote 표기·BOM·행 종결 문자를 포함한 원래 byte 파일은 보존하지 않는다. source record number는 물리적 줄 번호가 아닌 논리 record 번호다. 최초 source와 나중 overlap source를 구분하고, 배분·취소는 원금과 source 증빙을 바꾸지 않는다. 기존 payment DTO는 유지하고 `BANK_CSV` 값과 별도 source GET을 추가한다.
- importer는 parser 원문 예외를 안전한 고정 오류로 바꾸고 controller는 `DataAccessException`을 SQL 없는 503으로 처리한다. 공통 `MultipartException` handler는 client header/filename이 섞일 수 있는 예외를 logging fallback으로 보내지 않고 400을 반환하며 기존 oversized transport 413 handler가 더 구체적이다. 이는 검토한 오류 경로의 평가이며 모든 framework/commit 장애에 대한 로그 무유출 보증은 아니다.
- 새 코드에는 AI/provider·legacy 전역 금융 query·usage/quota 호출이나 자동 배분이 없다. withdrawal-only 이력은 데이터가 존재하면서 신규 입금이 0인 경우다. 이를 입금 누락으로 단정하지 않는다.

## 검증 근거

신규 통합 테스트 16개를 읽었다. 실제 security filter 아래의 route/direct-service 인증·tenant 경계·폐기 세션, BOM·multiline·정확한 원문과 Unicode 정규화, 동액의 별도 입금, file/key/account replay·병렬 경합·역순 overlap, 모든 financial identity conflict, 위조 stream 크기와 행 상한, late storage failure 전체 rollback·key 재사용, source FK/sourceKind 거절, 배분·취소 후 원문 불변을 포함한다. AI client와 legacy query mock 무호출도 매 테스트 후 확인한다.

직접 읽은 targeted XML은 `BankImportIntegrationTest` 16개, failure/error/skip 0이다. `/workspace/.local/run/import-targeted.log`는 `BUILD SUCCESSFUL in 34s`를 기록한다. 최종 `backend-java/build/test-results/test/TEST-*.xml` 20개도 직접 합산해 전체 165개와 bank import 16개 모두 failure/error/skip 0임을 확인했다. `/workspace/.local/run/import-full-build.log`에는 `bootJar`, `jar`, `test`, `check`, `build`와 `BUILD SUCCESSFUL in 1m 19s`가 있다.

실제 HTTP smoke script와 `/workspace/.local/run/import-http-smoke.log`도 읽었다. 9개 scenario는 health, 익명 401, malformed multipart 400, 별도 입금 2건+출금 1건, 같은 key/file replay, 원문 source 조회, 잘못된 파일의 부분 저장 부재, matching 후보 연결, 묶음 배분 후 미수 20000과 원문 연결 보존이다. 최종 script는 matching 결과의 payment ID 집합이 가져온 두 payment ID와 같고, 각각 새 invoice ID와 `suggestedAmount=40000`인 후보가 있음을 검사한다. `/workspace/.local/run/import-http-app.log`는 `reconciliation_test` 사용을 기록하며 malformed multipart sentinel와 `Unhandled exception` 문자열은 없었다. smoke log는 자기 합성 tenant 정리 완료를 기록한다. 이 검증은 malformed multipart와 검사한 오류 경로에 대한 결과이며 모든 서버 장애의 로그 내용 검증은 아니다.

별도 `/workspace/.local/run/import-dev-app.log`에서 `reconciliation_dev`의 V12 적용과 정상 시작을 확인했다. `import-dev-before.txt`와 `import-dev-after.txt`의 기존 금융 row-count snapshot은 모두 `1|1|1|2`다. 이는 기존 자료 개수 유지와 migration/startup 근거이며 기존 모든 셀의 byte 동일성이나 개발 DB 기능 테스트를 뜻하지 않는다. HTTP 시나리오는 테스트 DB에서 실행됐다. 실제 모델 요청 수를 측정하는 계측 검증은 하지 않았으며 새 소스의 AI 호출 부재와 테스트 mock 무호출을 별도로 확인했다.

## 범위와 남은 단계

수금 가져오기는 matching의 별도 200 payment/200 invoice 상한을 늘리지 않는다. 월별 성공 실행 quota, 모바일, 보존·삭제와 누적 저장 예산 및 공개 운영 제어는 후속 기능이다. 현재 검토는 내부 사전 출시 기능의 검토이며 공개 FREE 출시 승인이 아니다.
