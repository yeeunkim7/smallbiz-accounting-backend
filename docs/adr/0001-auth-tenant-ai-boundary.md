# ADR 0001: 로컬 계정과 사업체별 접근 경계

상태: 2026-11-01 첫 FREE 기반 기능에서 채택·구현된 설계 기록. 공개 출시 승인을 뜻하지 않는다. 원문은 `Accepted`로 기록됐으나 인증 방식의 개별 대안을 개발자가 직접 비교·승인했다는 근거는 확인되지 않는다. 이 문서는 에이전트의 구현 결정과 현재 코드에서 확인되는 경계를 정리한다.

## 문제와 판단의 출처

기존 대사 자료에는 사업체 소유권이 없어 계정만 추가해 기존 전역 원본을 노출할 수 없었다. 사용자 요구는 사업체별 격리, 서버 등급 확인, FREE의 AI 호출 금지와 기존 자료 보존이었다. 로컬 계정·opaque 세션·차단 기본값은 이를 구현한 방식이다.

외부 인증 공급자·JWT 등과 실제 비교해 선택했다는 기록은 없다. 해당 대안을 검토한 이력으로 소급 작성하지 않는다. 외부 유료 인증·메일 서비스는 도입하지 않았다.

## 구현한 결정

Spring Security의 stateless filter chain과 BCrypt(cost 12)를 사용한다. 대소문자를 구분하지 않도록 정규화한 ASCII login은 식별자이며 인증된 이메일이 아니다. 가입은 계정, FREE 사업체, OWNER membership을 하나의 트랜잭션으로 만든다. 등급은 서버/DB 정책으로만 변경하며 승급·membership 변경 API는 없다. 기존 금융 원본을 새 계정에 귀속하지 않는다.

세션은 안전한 난수 32바이트를 URL-safe opaque Bearer 토큰으로 만든다. DB에는 SHA-256 해시만 저장하고 원문 토큰은 가입·로그인 응답에 한 번만 반환한다. `me`나 로그로 반환하지 않는다. 만료는 서버 UTC epoch와 `app.auth.session-ttl-seconds`(기본 86400)를 사용한다. 로그아웃은 현재 세션을 철회한다. 자격 정보는 Bearer 헤더만 받으며 쿠키·HTTP session·form login·basic auth·remember-me는 사용하지 않는다.

새 API 경계는 `/api/v1/auth/{signup,login,me,logout}`와 `/api/v1/tenants/{id}`다. 사업체 조회는 인증된 서버 principal과 membership을 연결한다. 클라이언트가 제시한 ID·plan은 권한 근거가 아니며 가입·로그인 JSON의 미허용 필드는 거부한다.

기존 `/api/v1` 금융·검토 API는 기본적으로 익명 401, 가입 계정 403이다. 기존 응답 계약의 회귀검증을 위해 격리 로컬 환경에서 `legacy-dev` 프로필과 `app.legacy.enabled=true`를 모두 설정한 경우에만 익명 legacy 호출을 허용한다. 이는 전역 자료를 노출하므로 공개 서버나 사업체별 트래픽에 사용하지 않는다. 기존 회귀 테스트는 이 설정을 명시적으로 선택하고 보안 테스트는 비활성화한다.

AI 검토와 provider completion은 각각 조회/HTTP 호출 전에 서버 정책을 검사한다. 익명·FREE·STANDARD는 거부한다. PRO도 기본 비활성 `app.ai.enabled`와 사업체 소유권 조건을 통과해야 한다. 현재 보존된 legacy 도구에는 사업체 소유권이 없어 스위치를 켜도 거부하며, 이를 우회하는 설정은 없다. capabilities는 `configured=false`를 반환하고 provider를 확인하지 않는다. 기존 알고리즘/전송 단위 테스트의 정책 mock은 해당 자산의 검사에만 사용한다.

## 반영 범위와 트레이드오프

V8은 account·tenant·membership·session만 추가했다. 이 ADR의 최초 기능 범위에는 미수금, 배분, 이용량, 결제, 모바일이 없었다. 이후 V9–V13 및 Expo 구현 상태는 [현재 계획](../RECEIVABLES_FREE_MVP_PLAN.md)을 따른다.

DB 세션은 서버에서 철회를 확인할 수 있지만 세션 저장·조회·만료 관리가 필요하다. 로컬 인증을 사용한 만큼 비밀번호 복구와 가입 남용 방지 책임도 서비스에 남는다. 외부 인증 연동으로 인한 성능·비용 절감 효과를 측정한 기록은 없다.

## 검증과 남은 결정

마이그레이션 검증은 `reconciliation_test`에서만 수행하며 V1–V7과 개발 금융 원본을 보존한다. 구현·검증 근거는 [인증 문서](../AUTH_FOUNDATION.md), [독립 보안 검토](../auth-security-review.md), [V8](../../backend-java/src/main/resources/db/migration/V8__account_tenant_security.sql)에 있다. 문서 수정으로 테스트를 다시 실행한 것은 아니다.

공개 출시에는 TLS, 실제 기기의 안전한 토큰 보관, 비밀번호 재설정, 이메일 확인, 계정 삭제·보존, 로그인 제한과 가입 남용 방지가 필요하다. 임의의 복구 우회 기능을 추가하지 않으며 운영 정책·외부 서비스 도입 등 주요 변경은 개발자 결정을 받아 진행한다.
