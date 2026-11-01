# 인증·사업체 경계 독립 보안 검토

2026-11-01. 검토 대상은 이번 로컬 인증 기반 구현이며 공개 출시 승인이 아니다. 검토자는 코드와 설계를 읽고 개발자에게 결함을 전달한다. 자동 테스트와 DB 검증은 총괄/구현 담당이 수행하며, 이 문서에서 별도 실행했다고 표시하지 않는다. 실제 모델 호출은 수행하지 않는다.

## 검토 상태

최종 코드 읽기와 `git diff --check`를 완료했다. 이번 범위에서 남은 P1/P2 접근 제어 결함은 찾지 못했다. 구현 담당은 전체 103 tests/16 XML, 실패·오류·skip 0을 보고했다. 검토자가 이를 재실행한 것은 아니며 최종 실행 증거는 총괄/구현 담당 기록을 따른다.

## 발견 사항과 수정 확인

1. 최초 AI policy는 principal만 확인해 이미 필터를 통과한 세션의 폐기·만료를 다시 읽지 않았다. 개발자에게 전달했고, `AuthService.requireActiveSession()`이 세션 hash·account ID·폐기 여부·서버 시각 만료를 DB에서 재검증하도록 수정한 코드를 확인했다. `me`, tenant 조회와 AI policy가 이 검증을 사용한다. AI 서비스의 시작·최초 금융 조회·각 provider round·각 tool 조회·마지막 summary 재조회와 provider의 직접 호출에도 policy가 있다.
2. 첫 가입 테스트는 account INSERT 중복만 검사해 후반 실패 롤백 증거가 부족했다. 개발자에게 전달했고 최종 session INSERT를 강제 실패시켜 네 신규 테이블이 모두 비어 있는지 확인하는 테스트를 추가한 것을 확인했다. 이 테스트는 격리가드가 있는 테스트 DB에 임시 CHECK를 설치하고 finally에서 제거한다.
3. `capabilities`의 기존 `model` 필드 제거를 알렸고, provider를 조회하지 않는 빈 문자열 필드로 유지한 코드를 확인했다. 계정 저장 오류는 controller의 안전한 503 응답으로 변환하며 SQL 예외 상세를 기록하지 않는다.

## 확인한 경계와 검증 코드

- `AuthService`: BCrypt cost 12, 12자 이상/UTF-8 72바이트 이하, 정규화 login, 미존재 login에도 dummy BCrypt 비교, 사용자·FREE tenant·OWNER membership·세션을 하나의 트랜잭션으로 생성. 원문 bearer는 발급 응답에서만 반환하고 DB에는 SHA-256만 저장한다. 로그인 실패는 미존재/잘못된 비밀번호에 같은 응답이다. 가입의 `LOGIN_UNAVAILABLE`은 식별자 존재를 드러내므로 공개 가입 정책에서 별도로 다룬다.
- `AccountSecurityConfiguration`: stateless, cookie/form/basic/remember-me 인증 경로 없음, request cache 없음, API no-store, 잘못되거나 중복된 Authorization 거부. Spring Security 기본 firewall 사용과 servlet/context path 기준 분기, 요청 종료 후 context 정리는 추가 테스트에 포함된다. 명시적 `UserDetailsService` 빈이 Boot의 기본 생성 사용자/비밀번호 구성을 대체한다.
- `TenantAccess`와 V8: principal account ID와 membership을 조인하며 미존재/타 계정 tenant에 같은 403. role은 OWNER만 허용하고 관계는 FK로 제한한다. V8은 신규 네 테이블·인덱스만 추가하며 V1–V7과 기존 금융 행을 변경하거나 연결하지 않는다.
- legacy: 기본 익명 401/계정 403. `legacy-dev`와 `app.legacy.enabled=true`가 모두 필요한 익명 fixture만 예외다. flag만 있는 경우, profile만 있는 경우, 둘 다 있는 fixture의 계정/잘못된 bearer/AI 요청 거부를 분리한 테스트가 있다. 기존 회귀 테스트의 opt-in은 test source에 있다.
- AI: FREE/STANDARD/무인증/요청 plan 위조/PRO+switch-on, 직접 service/provider 호출, 폐기된 principal에서 금융 query와 model client 0회 검증 코드가 있다. 기존 알고리즘/HTTP unit test만 policy를 mock한다. 현 실제 policy는 tenant 도구 미준비 때문에 PRO도 항상 거부한다.

검토자는 위 테스트 코드를 읽었으며 중복 DB 테스트나 실제 모델 호출을 실행하지 않았다. HTTP server를 별도로 시작하거나 실제 브라우저를 검증하지 않았다.

## 범위와 기준

- 비밀번호 BCrypt 저장과 UTF-8 입력 길이 제한, login 정규화 및 로그인 실패 응답, 256비트 opaque bearer 발급·SHA-256 저장·만료·로그아웃 폐기. login은 확인된 이메일 주소가 아니다.
- 사용자·FREE 사업체·OWNER membership·세션 생성의 원자성, additive V8과 기존 금융 원본 비수정, 다른 사용자의 사업체 ID 조회 거부.
- stateless 인증 컨텍스트와 URL 정규화/방화벽, 기본 legacy 전역 API 거부, `legacy-dev` 프로필과 명시적 opt-in이 모두 있는 익명 개발 접근만 예외.
- AI 경로와 직접 서비스 호출의 인증→membership→서버 entitlement→kill-switch→tenant 도구 준비 확인. 기존 전역 읽기 도구를 가진 현 단계에는 PRO도 거부한다. 시작·각 조회·각 provider 요청 전에 재검증한다.
- 설정·키 존재나 요청의 plan 필드가 권한을 부여하지 않으며 거부된 요청은 금융 조회와 모델 요청에 도달하지 않는다.

## 공개 출시 전 별도 필수 작업

기본 로그인/가입 rate limit, credential stuffing·비밀번호 추측 방어와 관측은 아직 별도 출시 게이트다. BCrypt만으로 온라인 brute force를 막을 수 없고 가입도 CPU/DB 자원을 소모한다. 이메일 소유권 확인, 비밀번호 복구, 계정 삭제·세션 전체 폐기 정책, HTTPS 운영과 클라이언트 토큰 보관 방식도 공개 계정 운영 전에 확정·검증해야 한다.

향후 PRO를 허용하려면 tenant 읽기 도구, 유효기간을 포함한 entitlement, 비용/usage 한도와 모든 인스턴스에서 갱신 가능한 kill-switch가 먼저 필요하다. 현재 `app.ai.enabled`는 시작 시 읽는 설정이며 운영 중 동적 갱신을 구현하지 않았다. 마지막 도구 준비 거부는 설정으로 해제할 수 없다.

## 총괄 최종 실행 기록

리뷰 후 권한 폐기 회귀 테스트를 추가했고, 총괄이 전체 104개 테스트 성공(16개 XML, 실패·오류·스킵 0)을 확인했다. 로그는 `/workspace/.local/run/auth-final-tests.log`다. 이는 독립 검토자의 테스트 재실행으로 표시하지 않는다. 실제 로컬 서버에서도 기본 미인증 접근 차단, 가입 권한 필드 거절, V8 적용 및 기존 금융 행 수 보존을 확인했다. 실제 유료 AI 호출은 하지 않았다.
