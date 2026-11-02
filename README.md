# 차곡 — 미수금·입금 확인

**AI 에이전트를 활용해 개발한 개인 프로젝트입니다.**

기존 입출금 대사 시스템을 확장하면서 Codex의 총괄 에이전트 아래 기획·업무 분석, 자료조사, 디자인, 개발, 독립 검토 역할을 구성했습니다. 저는 업무 규칙과 완료 기준을 정하고, 에이전트가 저장소 분석부터 기능별 구현·실제 DB 및 브라우저 검증까지 이어가도록 작업을 조율했습니다. [Codex 선택 이유와 에이전트 협업 과정](docs/PORTFOLIO.md#ai-에이전트-활용-과정)

청구와 입금을 정리하고 남은 금액을 확인하는 서비스입니다. 입금이 나눠 들어오거나 여러 청구가 한꺼번에 처리된 경우에도 연결 내역을 따로 기록합니다. 잘못 연결한 내역은 사유를 남겨 취소할 수 있습니다.

## 주요 기능

- 거래처·청구·입금 등록과 은행 CSV 가져오기 API
- 날짜·금액·입금자명에 따른 연결 후보 조회
- 부분·묶음·분할 입금 연결 및 취소
- 미수 잔액, 감사 이력, 월별 이용량 조회
- 계정과 사업체별 접근 제어

금액은 Java와 DB에서 계산합니다. 화면에서는 후보와 현재 잔액을 확인한 뒤 사용자가 연결을 확정합니다.

## 포트폴리오

- [발표 PPT · 5장](docs/portfolio/chagok-project.pptx) · [PDF](docs/portfolio/chagok-project.pdf)
- [시연 영상 · 1분 46초](docs/demo/receivables-demo.mp4)
- [프로젝트 설명](docs/PORTFOLIO.md)
- [아키텍처](docs/architecture.md) · [ERD](docs/receivables-erd.md)

## 기술

- Java 21, Spring Boot, Gradle
- PostgreSQL, Flyway
- React Native, Expo, TypeScript

## 실행

[백엔드 설정](backend-java/README.md)과 [모바일 설정](mobile/README.md)에 따라 서버와 앱을 실행합니다.

```sh
cd mobile
npm ci
npm run web
```

앱 검사는 `npm test`, `npm run typecheck`로 실행합니다. 서버 연결 검증에는 별도 테스트 DB를 사용합니다.

[기본 화면 검증](docs/RECEIVABLES_MOBILE_VALIDATION.md) · [입금 연결 검증](docs/RECEIVABLES_MOBILE_MATCHING.md)
