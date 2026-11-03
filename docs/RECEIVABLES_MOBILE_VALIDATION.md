# Expo 모바일 코어 실행·검증

> 문서 범위: 이 문서는 기능4a의 8개 클라이언트 테스트·9개 브라우저 검증 기록이다. 당시 후속이었던 후보·확정·취소는 [기능4b](RECEIVABLES_MOBILE_MATCHING.md)에서 구현했다. 과거 결과를 현재 재실행한 것으로 보지 않는다.

이번 범위는 로그인/가입, 홈, 직접 등록, 장부·청구 잔액 상세, 계정·사용량과 별도 읽기 전용 샘플이다. 입금 연결 후보 검토·확정과 CSV의 모바일 화면은 다음 기능이며, 기존 백엔드 API는 보존한다.

## Windows와 로컬 실행

Java21/PostgreSQL17 개발 설정은 기존 백엔드 안내를 따른다. 개발 DB는 `reconciliation_dev`, 자동 테스트는 `reconciliation_test`로 분리한다. 백엔드 키·DB 비밀값을 Expo 설정에 복사하지 않는다.

백엔드 시작 전에 브라우저 주소만 명시한다. 아래는 PowerShell 예시다.

```powershell
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:8081,http://127.0.0.1:8081"
cd backend-java
.\gradlew.bat bootRun
```

다른 터미널에서:

```powershell
cd mobile
npm ci
$env:EXPO_PUBLIC_API_URL="http://localhost:8080"
npm run web -- --port 8081
```

앱 설정에는 공개 가능한 서버 주소만 둔다. 주소에 사용자 정보·키·query를 넣지 않는다. 서버 `app.cors.allowed-origins`는 쉼표로 구분한 정확한 HTTP(S) origin만 받으며 기본값은 빈 허용 목록이다. wildcard·주소의 경로·사용자 정보는 거부한다. 브라우저 사전 요청만 처리하고 실제 API의 Bearer 인증·사업체 권한 검사는 유지한다. 자격 증명 쿠키를 허용하지 않으며 허용 헤더는 Authorization/Content-Type/Idempotency-Key다. 기본 메서드는 GET/POST/OPTIONS이며 native 클라이언트에는 CORS가 필요하지 않다.

iPhone/Android의 `localhost`는 휴대폰 자신이다. Windows와 같은 사설 네트워크에서 시험하려면 공개 서버 주소 대신 개발 PC의 사설 IP를 설정하고 백엔드를 해당 네트워크에서 접근 가능하게 실행한다. Expo 앱은 `npm start -- --lan`으로 열 수 있으나 실제 Expo Go 호환·기기 방화벽·플랫폼 HTTP 정책은 별도로 확인해야 한다. 클라우드의 loopback 주소를 iPhone에서 바로 열 수 있다고 가정하지 않는다. 앱 서명/EAS/스토어 제출은 이번 단계에서 수행하지 않는다.

## 검증 상태

2026-11-01(서울), 합성 자료만 사용한 결과다. 실제 모델 호출·공개 배포·EAS·스토어 제출은 수행하지 않았다.

| 항목 | 결과 |
| --- | --- |
| 백엔드 | 전체186개(기존180+CORS6), 실패/오류/스킵0, XML22, build54초 성공 및 JAR 생성 |
| 앱 핵심 테스트 | npm test8개 통과. 키/세션 경합·저장 순서·URL·fetch receiver·정밀 금액/날짜 |
| TypeScript | npm run typecheck 통과 |
| 웹 export | EXPO_OFFLINE=1 npx expo export --platform web 성공, JS547KB+Ionicons390KB |
| Expo 의존성 | offline compatibility check 통과, 온라인 RN Directory 검사는 네트워크 정책으로 미완료 |
| 실제 Chromium | 9개 시나리오 통과, page error0,320/390/1280px 확인 |
| 개발 원본 | vendor/business_event/bank_transaction/upload_file 전후1/1/1/2. 건수 비교이며 행 내용 해시 검증 아님 |
| 네이티브 | iPhone/Android, SecureStore, VoiceOver/TalkBack, OS 글자 확대·키보드·safe area 미검증 |

브라우저9개 시나리오:

1. 샘플 홈→장부→청구 상세→계정→종료, API0회 및 가로 넘침 없음.
2. 실제 가입·사업체 생성 후 자료 없음 홈.
3. 실제 거래처·청구 등록, 서버 미수100,000원 상세.
4. 입금을 실제 서버에 저장한 뒤503으로 응답을 가린 시험에서 공백 수정 후 재전송: 동일 키, 입금1건 유지.
5. 미연결 입금40,000원을 등록해도 청구 미수100,000원 유지.
6. 서버 이용량 표시, 직접 등록의 성공 대사 차감0회.
7. 웹 localStorage/sessionStorage key0개.
8. 웹 새로고침 후 로그인 필요.
9. 실제 서버 세션 폐기 후 보호 화면 제거 및 만료 안내.

API는 전용 테스트 DB를 사용했고 만든 계정·사업체·금융 자료를 정리했다. 별도 독립 검토 fixture는320px에서 큰 합계9,007,199,254,740,993원·단건1,000,000,000,000원·긴 고객명·401을 확인했다. 이 값은 실제 tenant 자료나 서버 계산 검증이 아니다. 주요 금액은 생략/반올림 없이 읽을 수 있고 극단적으로 긴 값은 안내와 함께 가로로 읽는다.

실제 브라우저에서 발견한 raw fetch의 Illegal invocation, 동일 정규화 등록의 key 변경, 전송 중 탐색 경합,320px 금액 줄바꿈을 수정한 뒤 다시 검증했다. 보이는 버튼/탭44px, 역할·포커스·선택 상태를 확인했다. 주요 텍스트 대비는 본문/배경13.81:1, 보조/흰색5.24:1, 흰색/버튼6.06:1, hero 보조/배경5.65:1이다. 전체 접근성 인증이나 실제 OS 확대 검증을 뜻하지 않는다.

## 캡처·재현

[실제 홈](screenshots/mobile-core-live-home.png) · [등록](screenshots/mobile-core-registration.png) · [청구 상세](screenshots/mobile-core-invoice-detail.png) · [계정](screenshots/mobile-core-account.png) · [자료 없음](screenshots/mobile-core-empty.png) · [첫 화면](screenshots/mobile-core-login.png) · [샘플](screenshots/mobile-core-sample-home.png) · [데스크톱](screenshots/mobile-core-desktop.png) · [큰 금액 fixture](screenshots/mobile-core-large-fixture320.png) · [단건 fixture](screenshots/mobile-core-large-detail-fixture320.png)

검증 기본 주소는 API8091/Expo8081이다. API 서버를 반드시 reconciliation_test에 연결하고 EXPO_PUBLIC_API_URL=http://127.0.0.1:8091 및 서버 CORS origin8081을 설정한다. 실제 API 키는 비우고 app.ai.enabled=false를 유지한다. 합성 계정 정리에 필요한 TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD는 보안 환경 설정으로 주입한다. Python Playwright, Chromium, PostgreSQL CLI가 필요하다.

```sh
python scripts/verify-receivables-mobile-browser.py
```

스크립트는 loopback HTTP와 로컬 reconciliation_test 설정을 요구한다. 선택적 --web/--api/--screenshots 및 CHROMIUM_PATH를 지원한다. 클라우드에서는 /usr/bin/chromium을 사용했다. 실행 로그는 /workspace/.local/run/mobile-{full-build,unit-final,types-final,export-final,browser-final}.log다. [독립 검토](receivables-mobile-core-review.md).

다음은 입금 후보 검토·명시적 확정/취소와 CSV 모바일 흐름이다. 실제 페이지를 넘기는 장부/거래처 선택·복수 사업장 전환의 추가 렌더 검증, Windows 절차 실행과 실기기 검증도 남아 있다.
