# 모바일 입금 연결 검토 설계

구현 전 흐름 결정: 홈·장부·입금 연결·계정의 네 탭을 제공한다. 입금 연결은 AI 없이 기존 tenant API만 사용한다. CSV 가져오기 UI는 후속 범위다. 플랫폼별 API/세션 어댑터를 유지하며 Apps in Toss SDK는 추가하지 않는다.

1. 서버 월 사용량과 초기화 시각을 조회한다. 날짜 범위를 입력하고 성공 저장 시 1회(0건 포함)라는 안내를 읽은 뒤 명시적으로 후보를 생성한다. 기존 실행 목록·상세 GET은 무료이며 한도 소진 후에도 열린다.
2. 저장 시각이 표시된 불변 snapshot의 입금자·거래처·청구번호·날짜·정확한 금액과 한국어 근거/경고를 검토한다. 자료 없음/입금만/청구만/양쪽 자료 있음과 후보 0건을 구분한다. 후보 없음은 미입금·파일 누락 증거가 아니다. 사용자가 원본 보기를 요청한 경우에만 tenant payment source GET을 호출하고, 응답이 없으면 출처 미확인으로 표시한다.
3. 사용자가 후보 또는 직접 선택한 청구를 초안에 담는다. 같은 입금의 여러 청구, 같은 청구의 여러 입금을 허용하되 쌍은 유일하다. 금액은 1~1조 원 정수 문자열이다. 후보 제안액은 서버 응답만 사용한다. 비후보 선택은 사용자 직접 판단임을 표시한다. 합계/예상 잔액을 클라이언트가 계산하지 않는다.
4. ‘현재 잔액 검토’ GET으로 각 행 양쪽의 현재 잔액을 조회한 뒤 확인 단계가 열린다. 이 단계에서 ‘연결 확정’을 한 번 더 눌러야 POST한다. POST는 lines와 선택 reason만 보내며 서버가 잠금 아래 현재 잔액을 재검증한다. 409/초과는 초안을 보존하고 다시 조회하도록 안내한다. 확정 후 홈·장부·사용량·이력을 갱신한다.
5. 페이지 이력 상세에서 원래 행·금액·사유·시각·상태·감사 이력을 조회한다. 전체 연결 취소는 필수 사유 입력 → 별도 확인 → 명시적 취소 POST(reason만)의 순서다. 불변 명령 응답과 현재 상태를 구분하기 위해 성공/재전송 후 상세 GET을 다시 한다.

명령 키는 사업장 메모리에 보관한다. 정규화된 동일 payload는 불확실한 실패·화면 이동 후에도 같은 키이며 내용 변경은 새 키다. 자동 재시도는 없다. POST 동안 입력·탭·Android 뒤로가기를 잠근다. 세션/사업장 변경은 기존 앱 경계를 따른다. 서버 quota 오류의 사용량/초기화도 표시하고 월 변경은 같은 키로 수동 재시도를 허용한다.

샘플은 기존 합성 자료 읽기 전용을 보존하고 입금 연결 탭에서 안내만 표시한다. API·사용량·금융 변경을 호출하지 않는다. 네이티브 기기 검증은 별도이며 웹 검증을 기기 검증으로 기록하지 않는다.

구현 검증: `npm test` 13개(기존 8개 + 초안/정규화 키/확정 조건/날짜 범위/서버 한도 오류 5개), `npm run typecheck`, `expo export --platform web`를 통과했다. 이력 목록은 batch 요약만 표시하고 상세 행은 10개씩 추가 조회 표시한다. CSV 출처를 사용자가 열면 현재 입금의 sourceKind를 먼저 조회하고 BANK_CSV만 원본·파일 메타데이터를 조회한다. MANUAL은 서버에 직접 등록된 자료임을 표시한다. 실제 브라우저·백엔드 통합 검증 결과는 별도 검증 기록을 따른다.

## 실제 검증·캡처

2026-11-01 서울 기준. 기존 코어의 실제 가입·등록·조회·세션 회귀9개와 새 연결 흐름9개가 실제 Java API에 연결된 Chromium에서 통과했고 page error는0개다. 별도 독립 검토 fixture11개, 앱 핵심13개(기존8+신규5), TypeScript와 웹 export도 통과했다. 백엔드 수정 없이 전체186개가 실패/오류/스킵0으로 통과했고 JAR build는1분3초였다.

실제 서버 시나리오는 청구A100,000원/B50,000원과 입금40,000원/60,000원/150,000원을 사용한다.

- 후보 생성만으로 배분되지 않고 저장 성공1회만 사용량 반영.
- 40,000원 부분 연결 후 미수110,000원, 사유 취소 후150,000원 복구.
- 40,000+60,000원 묶음 연결 후 미수50,000원, 취소 후 복구.
- 입금150,000원을 두 청구100,000/50,000원으로 나눠 연결 후 미수0원, 전체 취소 후150,000원 복구.
- 확정은 실제 서버에 저장됐지만503 응답으로 가린 시험에서 탭 왕복 뒤 같은 키로 재시도해 배분1건만 유지.
- 세 연결의 원래 행과 확정/취소 감사 이벤트를 보존. 확정·취소·조회는 후보 생성 횟수를 추가 사용하지 않음.
- 실제 월 한도10회 소진 후429와 기존 결과 무료 조회 확인.320px 가로 문서 넘침 없음.

독립 fixture는 불확실한 취소 재시도, 취소 중 탐색 잠금, 지연 GET 경합, 원래 확정v1 재응답 후 현재 취소v2 분리, POST성공 뒤 조회 실패, 큰 이력의 불필요 조회 차단을 별도로 확인했다. fixture는 실제 금융 계산 근거와 구분한다. 상세는 [독립 검토](receivables-mobile-matching-review.md).

[후보·근거](screenshots/mobile-matching-candidates.png) · [부분 연결](screenshots/mobile-matching-partial-confirm.png) · [분할 최종 검토](screenshots/mobile-matching-split-review.png) · [미수0원 홈](screenshots/mobile-matching-home-paid.png) · [취소·감사](screenshots/mobile-matching-cancelled.png) · [이력](screenshots/mobile-matching-history.png)

### 재현

API를 반드시 reconciliation_test에 연결하고 loopback8091, Expo8081(API origin8091)로 실행한다. 실제 AI 키는 비우고 AI 스위치를 끈다. TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD는 기존 보안 환경 설정을 사용한다. Chromium·Python Playwright·PostgreSQL CLI가 필요하다.

```sh
cd mobile
npm ci
npm test
npm run typecheck
EXPO_OFFLINE=1 npx expo export --platform web
```

서버 실행은 [모바일 실행 안내](RECEIVABLES_MOBILE_VALIDATION.md)를 따른다. 저장소 루트에서:

```sh
python scripts/verify-mobile-matching-browser.py
python scripts/verify-receivables-mobile-browser.py --screenshots /tmp/core-regression
```

두 스크립트는 loopback HTTP와 로컬 전용 테스트 DB만 허용하고 합성 tenant 자료를 만든 뒤 정리한다. 실제 금융·개인정보는 사용하지 않는다. 클라우드 로그는 /workspace/.local/run/matching-mobile-{backend,browser-final,core-regression,unit,types}.log다. 개발 DB는 기존V12에서 이미 커밋된V13까지 환경만 복원했으며 원본 건수는 전후1/1/1/2(내용 해시 검증은 아님)다. 새 migration·Java 금액 로직 변경은 없다.

현재 범위의 미해결P1/P2는 발견되지 않았다. iPhone/Android·VoiceOver/TalkBack·OS글자 확대는 미검증이고, 소스 조회를 열어 실제 CSV 파일 식별정보를 읽는 경로는 구현·코드 검토까지만 수행했다. 이번 실제 서버 브라우저 데이터는 직접 등록 자료다. 상세의 표시 정보는 처음10행에서 최대40GET이며 추가행은 사용자 요청 때만 읽는다. 대량 자료/네이티브 성능은 후속 확인한다. 앱인토스 SDK 적합성·실행, 외부 배포·유료 API·영상 녹화는 이번에 수행하지 않았다. 다음은 완성된 흐름의 포트폴리오 시나리오·자료/영상 준비다.
