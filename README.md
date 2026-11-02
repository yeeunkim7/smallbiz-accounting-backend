# 소규모 사업자 미수금·입금 확인

후불 거래의 청구와 입금을 등록하고, 규칙이 제안한 연결을 사람이 확인한 뒤 부분·묶음 배분을 확정하는 Java/Spring Boot + Expo 프로젝트입니다. 현재 확정된 미수 잔액은 서버가 계산하고, 취소는 사유와 감사 기록을 남깁니다. 실제 고객·매출·성능을 입증하는 서비스 자료가 아니라, 합성 데이터로 흐름과 경계를 확인한 개발 포트폴리오입니다.

**개발 방식: AI 에이전트를 활용한 개인 프로젝트.** 사용자가 업무 문제·요구사항·제약과 우선순위를 정하고, 총괄·분석·개발·독립 검토 역할의 AI 에이전트를 조율해 기존 프로젝트를 확장했습니다. 구현뿐 아니라 테스트 실행·브라우저 검증·문서·시연 영상 제작에도 에이전트를 활용했습니다. 이는 개발 과정의 AI 활용이며, 현재 FREE 업무 흐름에서 AI 모델을 호출하거나 AI가 입금 연결을 확정한다는 뜻은 아닙니다. [역할 분담·실제 수정 사례](docs/PORTFOLIO.md#ai-에이전트를-활용한-개발-과정)

**현재 구현 흐름:** 로그인/사업체 → 거래처·청구·입금 등록 또는 서버 CSV 가져오기 API → 규칙 후보 확인 → 사람의 부분/묶음/분할 선택 → 서버에서 현재 잔액 검증 후 확정 → 미수·이력 조회 → 사유를 남겨 취소·복구.

## 시연과 문서

- [포트폴리오 개요·검증 범위·남은 게이트](docs/PORTFOLIO.md)
- [구성도와 설계 결정](docs/architecture.md)
- [핵심 데이터 ERD](docs/receivables-erd.md)
- [실행 및 검증 기록](docs/RECEIVABLES_MOBILE_MATCHING.md)
- [시연 영상 MP4 · 1분46초](docs/demo/receivables-demo.mp4) · [자막·재현 안내](docs/demo/README.md)
- [제출용 프로젝트 소개 PDF · 2쪽](docs/portfolio/overview.pdf)
- [기존 전역 입출금 대사 README 보존본](docs/LEGACY_RECONCILIATION.md)

## 기술 구성

- 서버: Java 21, Spring Boot, PostgreSQL, Flyway, Gradle
- 화면: React Native + Expo + TypeScript (웹 export로 실제 브라우저 경로 검증)
- 인증: 서버 세션과 사업체 멤버십 확인, tenant 단위 데이터 접근
- 금액: DB 원화 `BIGINT`, Java 합계 `BigInteger`, API 금액 문자열

기존 전역 입출금 합계 대사와 읽기 전용 AI 검토는 별도 legacy 경로로 보존합니다. 신규 tenant 미수 기능은 legacy 행을 계정에 자동 연결하지 않습니다. FREE/STANDARD에서는 모델 호출이 차단되며, PRO도 tenant 전용 AI 도구가 준비될 때까지 막혀 있습니다.

## 로컬 실행

먼저 [Java 백엔드 실행 안내](backend-java/README.md)에서 PostgreSQL 연결과 필요한 환경 설정을 준비하고 서버를 실행합니다. 이어서 [모바일 검증 안내](docs/RECEIVABLES_MOBILE_VALIDATION.md)의 Expo 실행 절차로 앱을 띄웁니다. 설정 값과 비밀 값은 저장소에 기록하지 말고 로컬 환경에서만 제공하세요. 환경 설정이 이미 된 검증 환경에서는 다음 명령으로 클라이언트 코드를 확인할 수 있습니다.

```sh
cd mobile
npm ci
npm test
npm run typecheck
EXPO_OFFLINE=1 npx expo export --platform web
```

이 명령은 클라이언트 단위·타입·웹 export 확인입니다. 서버 연결 검증에는 격리된 `reconciliation_test` 데이터베이스와 로컬 Chromium 절차가 필요하며, 그 재현 방법과 기존 실행 결과는 [모바일 연결 검증 기록](docs/RECEIVABLES_MOBILE_MATCHING.md)에 있습니다. 문서에 적힌 테스트 결과는 각각 기록된 당시 실행에 대한 것입니다.

## 범위

현재 시연은 사람의 검토와 서버 확정으로 채권 잔액을 갱신합니다. 규칙 후보는 근거를 제시할 뿐 금액을 자동 연결하지 않으며, 후보가 없다는 사실만으로 입금 누락이나 파일 미제출을 결론 내리지 않습니다. 수동 연결·취소·조회는 월별 성공 후보 실행 사용량에 포함되지 않습니다.

무료 공개 서비스, 모바일 CSV 화면, iOS/Android 실기기 및 접근성 검증, 앱인토스 적용, 스토어·결제, 공개 배포, 운영 개인정보·삭제 정책은 아직 확인이 끝나지 않았습니다. 앱인토스는 기존 Expo 앱을 그대로 업로드한다고 가정하지 않고, 서비스 적합성·현재 SDK/정책 확인 후 클라이언트 범위를 정할 예정입니다. 가격과 월별 무료 한도는 검증할 가설이며 판매 조건이 아닙니다.
