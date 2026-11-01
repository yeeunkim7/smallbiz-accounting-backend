# 계정·사업체·AI 접근 경계

2026-11-01. FREE MVP의 첫 백엔드 기능 단위다. 모바일 로그인 화면·청구·입금 배분·사용량·결제는 이번 범위에 포함하지 않는다. 기존 AI 자산은 보존하지만 계정에 연결할 tenant 전용 조회 도구가 없어 PRO도 아직 실행할 수 없다.

## 계정 API

| 요청 | 계약 |
| --- | --- |
| POST `/api/v1/auth/signup` | JSON login/password/tenantName만 허용. FREE 사업체와 OWNER 멤버십 생성 후 세션 발급 |
| POST `/api/v1/auth/login` | JSON login/password. 새 bearer 세션 발급 |
| GET `/api/v1/auth/me` | 인증된 계정과 실제 소속 사업체·서버 등급 반환 |
| POST `/api/v1/auth/logout` | 현재 bearer 세션 폐기 |
| GET `/api/v1/tenants/{id}` | 인증 계정의 멤버십이 있는 사업체만 조회 |

login은 정규화된 식별자이며 검증된 이메일을 뜻하지 않는다. 비밀번호는 최소 12자, UTF-8 최대 72바이트이며 BCrypt로 저장한다. 세션 토큰은 난수 32바이트로 생성해 발급 응답에만 반환하고 DB에는 SHA-256 해시만 보관한다. 이후 요청은 `Authorization: Bearer <token>`을 사용한다. 토큰·비밀번호를 로그/스크린샷/소스에 남기지 않는다.

서버 설정 `app.auth.session-ttl-seconds`의 기본값은 86400이다. 만료·폐기된 세션을 재사용하지 않으며, 기기 시각이 아닌 서버 UTC 시각으로 판단한다. 세션은 쿠키가 아닌 bearer 전용이다. 모바일 구현 시 안전한 기기 저장소를 검토하고 운영에서는 HTTPS가 필요하다.

회원가입 요청의 plan/role/tenantId 같은 권한 필드는 거절한다. 등급 상승·사업체 멤버 변경 API는 공개하지 않는다. 다른 사용자의 사업체 ID를 제시해도 소유권이 생기지 않는다. V8은 계정·사업체·멤버십·세션 테이블만 추가하며 기존 금융 행에 소유자를 임의 지정하지 않는다.

## 기존 대사 API와 보안 기본값

기존 `/api/**` 전역 금융 데이터에는 tenant 소유권이 없다. 기본값에서는 미인증 요청을 거절하고, 등록된 계정으로도 기존 전역 자료를 읽거나 변경할 수 없다. 응답 데이터 구조와 기존 업무 로직은 보존한다.

기존 화면을 격리된 로컬 개발에서 확인하려면 **legacy-dev 프로파일과 app.legacy.enabled=true를 모두 명시**한다. 이 예외는 익명 개발 요청에만 적용되며 bearer 계정의 전역 접근 우회로가 아니다. 운영/공유 서버에 사용하면 안 된다. 기존 통합 테스트도 이 명시적 fixture를 선택하고 신규 보안 테스트는 secure default를 검증한다.

AI POST와 외부 HTTP completion은 별도의 서버 정책을 확인한다. FREE/STANDARD/미인증은 모델 클라이언트에 도달하지 않는다. PRO도 기본 off kill-switch와 tenant 전용 도구 경계 때문에 현재는 거절된다. API 키가 존재하거나 `app.ai.enabled=true`를 설정하는 것만으로 전역 도구를 계정에 개방할 수 없다. capabilities의 configured는 해당 사용자에게 실제 허용되는 상태를 나타낸다.

## 검증 및 공개 출시 게이트

자동 테스트는 실제 키를 상속하지 않고 모의 모델/HTTP를 사용한다. 기존 테스트 DB 격리를 유지한다. 최종 실행 수와 독립 검토는 아래에 기록한다.

로그인/가입 rate limit·credential stuffing 방어, 이메일 소유권·복구·삭제·보존 및 전체 세션 폐기 정책, 운영 TLS와 모바일 토큰 보관은 공개 파일럿 전에 별도 구현·검증해야 한다. 이번 기능을 공개 서비스 출시 완료로 표시하지 않는다. 세부 결정은 [ADR](adr/0001-auth-tenant-ai-boundary.md), 검토는 [독립 검토](auth-security-review.md)를 따른다.

## 이번 실행 검증

전체 103개 테스트 통과 후, 모델 응답 직후 권한이 폐기될 때 다음 조회·모델 요청으로 진행하지 않는 회귀 테스트를 추가했다. 최종 전체 실행은 **104개 성공, 실패·오류·스킵 0**이다. 로그: `/workspace/.local/run/auth-final-tests.log`. 실제 HTTP에서는 health 200, 미인증 me/기존 vendor/AI review 401, 가입 요청의 PRO 필드 400을 확인했다. 이 스모크는 새 계정·금융 자료를 만들지 않았다. 개발 DB에 V8이 성공 적용됐으며 기존 금융 주요 행 수 1/1/1/2가 전후 동일했다. 자동 생성 비밀번호 로그도 0건이다.
