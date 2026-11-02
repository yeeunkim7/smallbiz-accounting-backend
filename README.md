# 차곡 — 미수금·입금 확인

청구와 입금을 연결하고 남은 금액을 확인하는 Java/Spring Boot + Expo 프로젝트입니다. 부분 입금, 묶음 입금, 한 입금의 분할 연결을 지원합니다. 연결을 취소하면 사유와 이력이 남고 잔액이 복구됩니다.

개발 과정에서 분석·구현·검토 역할의 AI 에이전트를 활용했습니다.

## 주요 기능

- 거래처·청구·입금 등록, 은행 CSV 가져오기 API
- 날짜·금액·입금자명을 기준으로 연결 후보 조회
- 현재 잔액 검토 후 사용자가 연결 확정
- 연결 취소, 감사 이력, 월별 이용량 조회
- 계정과 사업체별 데이터 접근 제어

## 시연·문서

- [시연 영상 · 1분 46초](docs/demo/receivables-demo.mp4)
- [프로젝트 소개 PDF](docs/portfolio/overview.pdf)
- [프로젝트 설명](docs/PORTFOLIO.md)
- [아키텍처](docs/architecture.md) · [ERD](docs/receivables-erd.md)

## 기술

| 구분 | 구성 |
| --- | --- |
| 서버 | Java 21, Spring Boot, Gradle |
| 데이터베이스 | PostgreSQL, Flyway |
| 클라이언트 | React Native, Expo, TypeScript |
| 금액 처리 | 정수 저장, 정확한 합산, API 문자열 전송 |

## 실행

[백엔드 설정](backend-java/README.md)과 [모바일 실행 안내](mobile/README.md)에 따라 서버와 앱을 실행합니다.

```sh
cd mobile
npm ci
npm run web
```

검사는 `npm test`, `npm run typecheck`로 실행합니다. 서버 연결 검증은 분리된 테스트 DB를 사용합니다.

[검증 기록](docs/RECEIVABLES_MOBILE_MATCHING.md) · [기존 입출금 대사 문서](docs/LEGACY_RECONCILIATION.md)
