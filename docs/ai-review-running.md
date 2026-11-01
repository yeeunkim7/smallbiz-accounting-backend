# 하루 AI 검토 실행·검증

## 현재 상태

GitHub `main`의 580adf5를 기반으로 구현했다. 요청자가 2026-10-31에 미커밋 988a983 비교 대신 GitHub 기준 진행을 확인했다. 기능은 로컬에서 동작하며 외부 공개 배포·환경 게시·서비스 결제는 하지 않았다.

- 조회·화면과 가짜 모델을 사용하는 도구 연결 테스트는 실행 가능하다.
- **실제 모델은 아직 검증하지 않았다.** 현재 서버 비밀 변수의 존재와 API 도메인의 허용 정책은 확인했지만 인증·모델 지원·요금은 미검증이다. 비용 승인이 완료되지 않아 실제 유료 호출은 계속 보류한다.
- `AI_MODEL=gpt-4.1-mini`는 모델 선택일 뿐 결제금액이나 예산 한도 설정이 아니다. API 키에 잔액을 지정하거나 이 앱이 월별 금액을 강제 제한하지 않는다. 공급자의 현재 요금·프로젝트 한도·알림 정책을 확인한 뒤 비용을 승인해야 한다.
- 현재 서버 제한은 동시 2건, 검토당 최대 5회 모델 요청(재시도 포함)/16회 도구 호출, 응답당 출력 최대 2,400토큰, HTTP 본문 1MiB/35초다. 이것은 달러 기준 비용 한도가 아니다. 한 번의 검토가 한 번의 모델 요청이라는 보장도 없다.

## API 사용 정책

1. 검토 한 번당 API 모델 요청은 **최대 5회**로 제한한다. 재시도도 총 요청 수에 포함한다.
2. **무한 반복·무한 재시도를 금지**한다. 한도 또는 종료 조건에 도달하면 중단한다.
3. 자동 테스트에서는 기본적으로 **실제 유료 API 호출을 금지**하고 모의 응답을 사용한다. 실제 유료 호출 검증은 명시적인 승인 후 별도로 실행한다.
4. API 키는 **서버의 비밀 설정에만 보관**한다. 소스·저장소·프런트엔드·문서·로그에는 키 값을 남기지 않는다.

2026-11-01 확인: `AiReviewService.MAX_MODEL_REQUESTS=5`가 실제 코드에 적용돼 있다. 불완전 근거 재요청과 반복 도구 요청이 모두 5회에서 종료되고, 5번째 유효 응답은 정상 완료하며, 공급자 오류는 자동 재시도하지 않는 경계 테스트를 통과했다.

## 실행 방법

일반 환경에는 JDK 21과 PostgreSQL이 필요하다. 기존 README의 `DB_*`, `TEST_DB_*` 설정을 사용하되 두 DB를 분리한다. 테스트 DB 격리 가드를 비활성화하지 않는다.

이 클라우드 환경은 준비된 `/workspace/.local/env.sh`와 기존 PostgreSQL 데이터를 사용한다:

```bash
source /workspace/.local/env.sh
pg_ctl -D /workspace/.local/pgdata status
# 정지한 경우에만:
pg_ctl -D /workspace/.local/pgdata \
  -l /workspace/.local/run/postgres.log \
  -o '-h 127.0.0.1 -p 5432 -k /workspace/.local/run' -w start
cd /workspace/smallbiz-accounting-backend/backend-java
bash gradlew --offline --no-daemon \
  --project-cache-dir /workspace/.local/gradle-project build
java -jar build/libs/reconciliation-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 --server.port=8082 --AI_API_KEY= \
  --spring.profiles.active=legacy-dev --app.legacy.enabled=true
```

위 실행 예는 외부에 노출하면 안 되는 기존 화면의 익명 로컬 개발 fixture다. 기본 보안 모드는 전역 금융API를 차단한다. 등록된 계정으로 이 예외를 사용할 수 없고 AI실행도 허용하지 않는다. 새 계정 API는 [인증 안내](AUTH_FOUNDATION.md)를 따른다.

위 실행 예는 비용 승인 전 화면 검증을 위해 앱의 AI 키 설정을 빈 값으로 덮어써 실제 호출을 막는다. 서버의 비밀 바인딩 자체를 지우거나 변경하지 않는다. 실제 모델 검증은 비용 승인뿐 아니라 인증된 PRO 권한과 tenant 전용 도구 구현 후 별도 절차가 필요하다. 현재는 키 인자를 제거해도 AI가 거절된다.

화면 경로는 `/ai-review.html`이다. 기존 화면 상단 `하루 AI 검토` 링크로도 이동한다. 새 빌드를 만들 때 실행 중 JAR를 덮어쓰지 말고 앱을 중지하거나 실행용 복사본을 사용한다. 화면의 날짜는 서울 시간 기준으로 시작한다.

기존 합성 데이터는 2026-10-31의 입금 12,345원 쌍이다. 예제 CSV는 빈 날짜 안내에서 내려받을 수 있다. 예제는 먼저 DEMO-PRE 선불 거래처를 등록하고 업무/은행 파일을 올리는 흐름이며, 현재 합성 데이터에 자동 추가하지 않았다. 같은 파일을 다시 올리면 기존 중복 방지 규칙이 적용된다.

## 조회 API

| 메서드·경로 | 인자·결과 |
| --- | --- |
| GET `/api/v1/ai-review/summary` | date. 입금·출금별 문자열 금액/차이, 건수, 자료 존재·비교 상태, 검토 상태, matchingStatus=NOT_EVALUATED |
| GET `/api/v1/ai-review/originals/business` | from/to, 선택 direction=IN/OUT, page/size. 기존 원본 DTO와 추적 ID |
| GET `/api/v1/ai-review/originals/bank` | 위와 동일. 은행 원본 상세 포함 |
| GET `/api/v1/ai-review/uploads` | from/to, page/size. 원본 거래일에 연결된 업로드이며 등록시각 필터가 아님 |
| GET `/api/v1/ai-review/capabilities` | configured/model. 키나 키 값은 반환하지 않음 |
| POST `/api/v1/ai-review/reviews` | `{ "date": "2026-10-31" }`. 조회만 실행해 결과 반환; 거래/검토 저장 없음 |

기간은 양끝 포함 최대 366일, 페이지는 0 이상, 크기는 1–100이다. 모델 도구는 요청한 하루에만 한정되며 더 작은 조회·호출 한도를 적용한다. 기존 API는 유지한다.

## AI 설정 — 관리자용

1. 환경 설정의 보안 입력란에서 `AI_API_KEY`를 추가한다. 값은 Git/채팅/로그에 남기지 않는다. `OPENAI_` 접두사는 이 환경의 비밀 변수 이름으로 예약돼 있어 앱은 `AI_API_KEY`를 사용한다.
2. 이 비밀값의 목적지는 `api.openai.com`으로 한정한다. HTTPS 허용 목록도 해당 도메인이 필요하다. 코드 기본 `AI_MODEL`은 `gpt-4.1-mini`다. 현재 환경은 `gpt-6-luna`로 덮어쓰고 있으며 이 이름의 공급자 지원·접근·요금은 검증하지 않았다. 사용자 설정은 변경하지 않았고 결제금액도 설정하지 않았다.
3. 현재 환경에 비밀 변수와 API 목적지 허용 설정이 존재하는 것은 확인했다. 실제 요청 성공을 확인한 것은 아니다. 변경이 필요하면 환경 설정에서 검토·저장하고 적용/게시 절차는 사용자가 결정한다. 에이전트는 게시하지 않았다.
4. 설정 적용 후 앱을 다시 시작한다. `/capabilities`의 configured는 키 존재 여부이며 인증·요금제·모델 접근 성공의 보장은 아니다.
5. 실제 호출 전 공급자 공식 요금과 한도를 확인하고 승인한다. `python scripts/verify-real-ai.py --date 2026-10-31 --allow-paid-call`은 실제 유료 호출을 할 수 있으므로 **현재 실행하지 않았다.**

AI 결과는 외부 AI 서비스로 선택한 날짜의 거래·파일 정보가 전송된 뒤 생성된다. 실제 고객 데이터 사용 전 개인정보·권한·외부 전송 정책을 검토해야 한다. 결과는 DB에 보관하지 않으며 사실은 Java 금액에 고정하고 추정·추가확인은 모델이 작성한다. 조회된 근거 ID 검증이 의미적 타당성을 완전히 보장하지는 않는다.

## 자동 검증

```bash
source /workspace/.local/env.sh
cd /workspace/smallbiz-accounting-backend/backend-java
bash gradlew --offline --no-daemon \
  --project-cache-dir /workspace/.local/gradle-project build --rerun-tasks
cd ..
python scripts/verify-ai-review-browser.py
```

Gradle `test` 작업은 호스트의 실제 키가 시험 JVM으로 상속되지 않도록 `AI_API_KEY`를 빈 값으로 설정한다. 모델·HTTP 테스트는 모의 클라이언트를 사용하며 실제 유료 검증 스크립트를 자동 실행하지 않는다.

브라우저 검증에는 Python Playwright와 Chromium이 필요하다. 기본 실행파일은 `/usr/bin/chromium`. 서버가 먼저 실행 중이어야 한다. 이 스크립트는 현재 복원된 합성 데이터(2026-10-31)에 대한 수동 스모크 검증용이며 빈 DB 전용 테스트가 아니다. AI 요청은 모두 가로채므로 실제 유료 호출을 하지 않는다. `docs/screenshots/browser-verification.json`과 PNG에 결과를 남긴다. 기존 캡처를 보존하려면 `--prefix nov01-`처럼 출력 접두사를 지정한다. 2026-11-01 결과는 `nov01-browser-verification.json`에 12개 흐름 성공으로 기록했다.

실제 앱 조회·무자료·모바일·키보드 확인과 고정 응답 AI 성공·오류·경합 검증을 구분한다. 화면의 고정 응답은 실제 모델 성공으로 보고하지 않는다.

## 출시 전 남은 작업

- 비용 승인 후 실제 모델 도구 호출·한국어 해석·인용 의미 검증, 지연·요금 측정.
- 국내·Excel·상용 직접 경쟁의 공식 비교 8개는 조사 문서에 보강했다. 실제 파일과 업무 흐름을 사용하는 국내 실무자 인터뷰로 수요·첫 사용·가격 수용성을 검증해야 한다.
- 인증/회사·계좌별 권한, 업로드·AI 요청 제한, 개인정보·외부 전송 정책과 운영 모니터링. 현재 앱은 격리된 로컬 개발용이며 공개 운영 준비가 끝난 서비스가 아니다.
- 모델 해석은 인용 ID가 실제 존재해도 틀릴 수 있다. 다양한 날짜·한쪽자료·지연입금·부분자료·주입문구로 평가 세트 확대.
- 현재 요약 변경 감지는 금액·건수·검토상태 기준이다. 동일 합계/건수로 원본이 교체되는 미래 수정 기능이 생기면 원본 버전·스냅샷 검증을 추가한다.

## 2026-11-01 재개 검증 결과

- 전체 Gradle 테스트 88개 성공, 실패·오류·스킵 0(기존56 + 조회8 + AI연결15 + HTTP전송9). `--rerun-tasks`로 실제 재실행했고 XML12개를 확인했다.
- Chromium 12개 흐름 성공, pageerror0. 독립 검토자는 별도 고정응답3개를 직접 검증하고 코드·전체 XML·모바일/변경요약 화면을 교차확인했다.
- 새 AI 응답이 더 최신 자료를 읽었으면 상단 요약도 해당 Java 결과로 갱신한다. 검토 중 자료 변경409는 AI재시도 대신 하루 재조회를 요구한다. 검토 근거와 아래 현재 원본 목록의 조회 시점 차이도 설명한다.
- 실제 유료 모델 호출은 0회이며, API키가 존재해도 지원모델·인증·요금·답변의 의미적 타당성을 검증한 것으로 표시하지 않는다.

## 인증 경계 도입 후 AI 상태

기존 실제모델 검증 스크립트는 향후 PRO 자산으로 보존했으며 지금은 실행 가능한 평가 경로가 아니다. 미인증 요청은401, FREE/STANDARD 및 tenant도구미완성PRO는거절된다. 키·app.ai.enabled만으로우회할수없고 legacy개발모드도AI권한을주지않는다. 현재FREE목표에실제모델호출은필요하지않다.
