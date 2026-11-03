# 은행 CSV 가져오기 실행·검증

> 문서 범위: 이 문서는 CSV 기능 단계의 실행 기록이다. 본문의 다음 단계·165개 테스트는 당시 기준이다. 이후 사용량과 Expo 구현 상태는 [현재 계획](RECEIVABLES_FREE_MVP_PLAN.md)을 따른다.

## 로컬 실행

Java21/PostgreSQL17에서 `backend-java/README.md`의 개발 DB 설정과 테스트 DB 분리 규칙을 따른다. 비밀값은 환경 설정으로 주입하며 파일·명령 기록·채팅에 쓰지 않는다. 개발 DB는 `reconciliation_dev`, 자동 검증은 `reconciliation_test`만 사용한다.

저장소 루트에서:

```sh
cd backend-java
bash gradlew build
bash gradlew bootRun
```

기존 가입·로그인 API의 세션을 사용한다. 로그인 응답의 토큰을 출력하지 말고 호출 도구의 비밀 변수에 저장한다. `GET /api/v1/auth/me`에서 자신의 사업체 ID를 확인한다. 아래 예시는 Unix 셸용이며 Windows에서는 Postman 등의 multipart 입력으로 같은 API를 호출할 수 있다.

```sh
curl --fail-with-body \
  -H "Authorization: Bearer ${SESSION_TOKEN}" \
  -H 'Idempotency-Key: synthetic-import-001' \
  -F 'bankAccountKey=SYNTHETIC-ACCOUNT' \
  -F 'file=@docs/examples/receivables-bank-valid.csv;type=text/csv' \
  "http://localhost:8080/api/v1/tenants/${TENANT_ID}/bank-imports"
```

이 샘플은 실제 금융정보가 아닌 합성 자료다. 같은 날짜·금액인 서로 다른 입금2건과 출금1건이 들어 있다. `bankAccountKey`는 계좌번호가 아닌 계좌 구분용 별칭이며 은행 소유권 인증을 뜻하지 않는다. 실제 파일은 계좌별로 일관된 별칭과 안정적인 거래 ID를 갖춰야 한다.

응답의 `id`로 `/bank-imports/{id}/rows`를 조회한다. `paymentId`로 `/payments/{paymentId}/source`를 조회하면 처음 등록된 원본 레코드로 돌아간다. 파일 재전송은 기존 이력을 반환하므로 원래 응답의 `importedCount`를 매번 새 입금으로 합산하지 않는다.

## 검증 상태

2026-11-01 새 환경에서 Java21.0.12.1/PostgreSQL17과 개발·테스트 DB 분리를 확인했다. 기존 V1~V11을 유지하고 V12를 정상 적용했다. 개발 원본 테이블 `vendor/business_event/bank_transaction/upload_file` 건수는 전후 `1/1/1/2`로 같았다. 이는 건수 비교 근거이며 모든 행의 내용 해시를 비교한 결과는 아니다.

검증 명령(관리형 클라우드, `backend-java`에서 실행):

```sh
source /workspace/.local/env.sh
bash gradlew --offline --no-daemon \
  --project-cache-dir /workspace/.local/gradle-project build --rerun-tasks
```

전체165개 테스트(기존149+신규16), 실패·오류·스킵0, XML20개를 확인했다. `BUILD SUCCESSFUL in 1m 19s`이며 JAR 생성도 성공했다. 기존 개발 DB 거부 초기화와 테스트 프로파일을 유지했고 Gradle은 실제 모델 키를 테스트에 전달하지 않는다. 첫 targeted 실행은 신규 테스트가 기존 취소 API에 허용되지 않은 `expectedVersion`을 보내 실패했다(15개 중1개). 기존 API를 바꾸지 않고 요청을 수정했으며 Unicode 경계 시나리오를 더한 최종16개와 전체 회귀가 통과했다.

빌드한 JAR를 테스트 DB에 연결한 로컬 서버에서 합성 자료로 다음9개 HTTP 시나리오를 통과했다. 실제 모델 키는 빈 값이며 AI 기능도 비활성화했다.

1. 상태 조회200.
2. 미인증 가져오기 이력 조회401.
3. 잘못된 multipart 요청400. 응답은 안전한 오류이고 앱 로그에 전송한 합성 sentinel이나 `Unhandled exception`이 없었다.
4. 같은 날짜·금액의 서로 다른 입금2건과 출금1건 등록: 입금만2건 생성.
5. 같은 key와 새 key의 동일 파일 재전송: 동일 import 반환, 중복 입금 없음.
6. 가져오기 원본 및 입금→최초 원본 역추적.
7. 잘못된 금액이 있는 파일 거부, 부분 저장 없음.
8. 가져온 입금이 기존 규칙 후보 API에 연결됨.
9. 두 입금을 청구100,000원에 수동 배분 후 미수20,000원, 원본 근거 불변.

검증이 만든 계정·사업체·금융 자료는 전용 테스트 DB에서만 사용하고 정리했다. 별도 개발 서버에서는 V12 적용과 `/health`200만 확인했다. 실제 AI 호출0회, 화면은 이번 작업 범위에 포함하지 않았다. HTTP 검증이 모바일 화면 검증을 대신하지 않는다.

관리형 환경의 실행 로그: `/workspace/.local/run/import-targeted.log`, `import-full-build.log`, `import-http-smoke.log`, `import-http-app.log`, `import-dev-app.log`. 재검토 결과는 [독립 검토](receivables-import-review.md)에 기록했다. 월별 성공 실행 한도와 모바일은 다음 작업이다.
