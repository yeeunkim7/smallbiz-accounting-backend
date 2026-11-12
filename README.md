# 차곡 — 미수금·입금 확인

청구와 입금을 연결하고, 아직 받지 못한 금액을 확인하는 서비스입니다. **Codex의 총괄·하위 AI 에이전트를 활용해 개발했습니다.**

## 개발 배경

기존 입출금 대사 시스템은 날짜별 예정 금액과 은행 내역을 비교했습니다. 하지만 합계가 같아도 어떤 청구가 지급됐는지는 알 수 없고, 부분 입금이나 여러 청구를 합친 입금을 관리하기 어렵습니다. 기존 대사 기능을 보존하면서 청구·입금·배분 이력을 별도 도메인으로 확장했습니다.

소규모 사업자의 후불 거래 관리를 대상으로 합니다. 실제 고객 수요와 사용성은 파일럿 검증이 남아 있습니다.

## 사용 흐름과 기능

청구 등록 → 입금 등록 → 연결 후보 조회 → 사용자 검토·확정 → 미수금 조회 → 필요 시 취소

- 거래처·청구·입금 직접 등록, 은행 CSV 가져오기 API와 원본 추적
- 날짜·금액·입금자명에 따른 후보 조회와 부분·묶음·분할 연결
- 현재 미수·기한 초과 잔액·미배분 입금 조회
- 사유를 남기는 연결 취소와 감사 이력
- 계정·사업체별 접근 제어, 월별 성공 후보 실행 이용량

청구 100,000원에 입금 40,000원을 연결하면 미수는 60,000원입니다. 입금 등록이나 후보 조회만으로 미수가 줄지는 않습니다. 자료 없음, 합계 일치, 개별 거래 연결은 서로 다른 상태입니다.

## 기술과 구조

Java 21 · Spring Boot · PostgreSQL 17 · Gradle · Flyway / React Native · Expo · TypeScript

모바일 앱이 사업체별 API를 호출하고, 신규 미수금 기능은 Controller → Service → JdbcTemplate의 SQL → PostgreSQL로 처리합니다. 기존 대사 기능의 JPA Repository 구조는 유지합니다. 금액 계산과 확정은 Java·DB에서 수행합니다.

[아키텍처와 호출 흐름](docs/architecture.md) · [테이블 관계](docs/receivables-erd.md)

## 주요 설계

- **응답 유실 후 재시도:** 사업체·작업별 요청 키와 내용을 보관합니다. 같은 요청은 기존 결과를 반환하고, 같은 키로 내용을 바꾸면 충돌로 처리합니다. [등록 계약](docs/RECEIVABLES_REGISTRATION.md)
- **동시 배분:** 입금·청구 부모 행을 일정한 순서로 잠근 뒤 잔액을 다시 검사합니다. 배분·감사 저장이 실패하면 함께 롤백합니다. [확정·취소 계약](docs/RECEIVABLES_ALLOCATION.md)
- **성공한 실행만 집계:** 후보 결과·사용량 이력·월별 횟수를 같은 트랜잭션에서 저장합니다. 재요청과 실패는 추가 차감하지 않습니다. [사용량 계약](docs/RECEIVABLES_USAGE.md)

선택 이유·대안·판단 주체는 [의사결정 기록](docs/adr/0002-receivables-decisions.md)에 구분했습니다.

## 실행과 테스트

[백엔드 설정](backend-java/README.md)에서 JDK 21과 PostgreSQL 개발·테스트 DB를 준비하고 환경 변수를 설정합니다. 비밀값은 저장소에 넣지 않습니다.

```sh
# backend-java 디렉터리에서 실행. Windows에서는 gradlew.bat 사용
bash gradlew bootRun
# TEST_DB_URL은 reconciliation_test여야 합니다.
bash gradlew test
```

[모바일 설정](mobile/README.md)에 따라 서버 주소와 브라우저 CORS를 설정합니다.

```sh
cd mobile
npm ci
npm run web
npm test
npm run typecheck
```

2026-11-01 기록: 백엔드 186개, 클라이언트 13개, 실제 브라우저·API 연결 9개와 기존 화면 회귀 9개 통과. 2026-11-04 사용자 PC에서도 백엔드 186개 통과(실패·오류·스킵 0)를 확인했습니다. 클라이언트·브라우저 수치는 기존 실행 기록입니다. [실행 근거와 미검증 범위](docs/RECEIVABLES_MOBILE_MATCHING.md)

## AI 에이전트 활용

Codex를 활용해 개발했습니다. 개발자가 서비스 목표·업무 규칙·범위·완료 기준을 정하고, 총괄 에이전트가 기획·분석, 자료조사, 디자인, 개발, 독립 검토 작업을 배분했습니다. 코드 구현과 테스트 실행에는 AI를 활용했습니다. 세부 설계 중 개발자의 직접 선택이 확인되지 않는 사항은 구현 사실과 구분합니다.

[개발자 결정 및 검토 절차](AGENTS.md) · [작업 이력](docs/AI_REVIEW_WORK.md)

개발 후에는 개인 PC의 Hermes에 Python 조회 도구를 연결해 실제 Java API의 미수 요약을 설명하는 실습을 진행했습니다. 제품 FREE 기능과 분리된 합성 자료 실습입니다. [소스와 확인 범위](examples/hermes/chagok-local-api)

## 현재 범위와 남은 작업

FREE 핵심 API와 Expo 화면의 등록→후보→확정→취소 흐름을 구현하고 브라우저에서 검증했습니다. FREE 업무는 AI를 호출하지 않습니다. 모바일 CSV 화면, iPhone·Android 실기기, 계정 복구·요청 제한·보존/삭제 정책과 공개 운영 검증이 남아 있습니다. 앱인토스 적용, 스토어 출시, 결제·구독과 PRO AI는 후속 범위입니다. Docker·CI·서버 운영은 저장소 정리와 기존 시스템 검토 이후 단계별 승인으로 진행할 예정이며, 아직 검증하지 않았습니다.

[현재 개발 계획](docs/RECEIVABLES_FREE_MVP_PLAN.md) · [문서 분류와 검토 결과](docs/DOCUMENTATION_REVIEW.md)
