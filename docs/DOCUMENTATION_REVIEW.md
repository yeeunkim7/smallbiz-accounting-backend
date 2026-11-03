# 문서 검토 결과

검토일: 2026-11-02 (Asia/Seoul). 기존 Markdown 44개(루트·docs·백엔드·모바일)를 분류하고 코드·기존 실행 기록과 대조했다. 아래 두 신규 문서는 이 검토 결과와 기존 결정 근거를 담는다. 기능 코드·DB·테스트 데이터·PPT·PDF·영상은 이번 범위 밖이며 변경하지 않았다. 푸시·공개 배포는 하지 않는다.

## 정리 기준과 우선순위

README·PORTFOLIO·AGENTS를 먼저 정리하고 기술 계약, 현재 계획, 과거 작업 기록 순서로 대조했다. 프로젝트 소개는 문제와 기능, 포트폴리오는 판단·검증 근거, 기술 문서는 현재 API·DB 계약, 작업 기록은 당시 날짜·실행 주체를 기준으로 읽도록 분리했다. 문서를 이동하거나 삭제하지 않았다.

- 역할별 활동 나열을 README에서 줄이고 상세 협업 과정은 PORTFOLIO에 둔다.
- LEGACY_RECONCILIATION의 신규 기능 반복은 개별 API 계약 링크로 줄인다. 당시 테스트·시점·오류 근거는 보존한다.
- FREE 계획의 오래된 “현재/다음”은 현황표와 과거 실행 이력으로 나눈다. 사용량 상세 계약은 전용 문서로 연결한다.
- 과거 검토 보고서는 본문을 다시 쓰지 않고 당시 단계와 현재 기준 링크를 붙인다. 영어로 작성된 기존 로그는 근거 보존을 위해 유지한다.
- ADR은 기존 체계를 재사용한다. 새 ADR 0002는 과거 기록의 정리이며 새로운 설계 승인이나 실제 비교실험을 뜻하지 않는다.

## 파일별 분류와 처리

| 파일 | 분류 | 처리 내용 |
| --- | --- | --- |
| [AGENTS.md](../AGENTS.md) | 에이전트 지침 | 주요 변경 승인·개발자 최종 검토·판단 출처 기록 추가, 기존 데이터·비용 규칙 유지 |
| [README.md](../README.md) | 프로젝트 소개 | 서비스·문제·기술·실행을 먼저 설명하고 AI 활용을 별도 절로 이동 |
| [backend-java/README.md](../backend-java/README.md) | 프로젝트 소개 / 기술 설계 | 실행·V1~V13·기본 접근 차단 갱신, 과거 45개와 이후 186개 검증 구분 |
| [backend-java/docs/design.md](../backend-java/docs/design.md) | 기술 설계 | legacy 범위·인증·화면·검토 저장의 오래된 설명 수정 |
| [docs/AI_REVIEW_WORK.md](AI_REVIEW_WORK.md) | 작업 기록 | 현재 요약과 과거 원문 분리; Objective 이하 기존 단계 이력 보존 |
| [docs/AUTH_FOUNDATION.md](AUTH_FOUNDATION.md) | 기술 설계 / 작업 기록 | 현재 토큰 저장 구현 링크와 당시 인증 단계 기록 구분 |
| [docs/LEGACY_RECONCILIATION.md](LEGACY_RECONCILIATION.md) | 프로젝트 소개 / 작업 기록 | 초기 범위·테이블 명시, 신규 기능 반복은 계약 링크화, 실무 경험은 확인 필요 |
| [docs/PORTFOLIO.md](PORTFOLIO.md) | 포트폴리오 | 문제·데이터 흐름·결정 출처·실제 결함 2개·검증 한계로 재구성 |
| [docs/PORTFOLIO_DELIVERY_PLAN.md](PORTFOLIO_DELIVERY_PLAN.md) | 포트폴리오 | 유지: 현재 계획·자료 기준 링크만 제공 |
| [docs/RECEIVABLES_ALLOCATION.md](RECEIVABLES_ALLOCATION.md) | 기술 설계 | 유지: 실제 API·락·롤백·잔액 계약과 일치 |
| [docs/RECEIVABLES_FREE_MVP_PLAN.md](RECEIVABLES_FREE_MVP_PLAN.md) | 의사결정 / 작업 기록 | 현재 구현·후속 우선순위 갱신; P0 이후 실행 이력 보존 |
| [docs/RECEIVABLES_IMPORT.md](RECEIVABLES_IMPORT.md) | 기술 설계 | 한국어 정리, API·상한·오류 계약 유지, 모바일 CSV 후속 구분 |
| [docs/RECEIVABLES_IMPORT_SMOKE.md](RECEIVABLES_IMPORT_SMOKE.md) | 작업 기록 | CSV 단계165개 검증 보존, 이후 사용량·모바일 구현 연결 |
| [docs/RECEIVABLES_MATCHING.md](RECEIVABLES_MATCHING.md) | 기술 설계 | 이미 구현한 Expo와 후속 실기기/운영 구분 |
| [docs/RECEIVABLES_MOBILE_CORE.md](RECEIVABLES_MOBILE_CORE.md) | 기술 설계 / 작업 기록 | 기능4a의 세 탭·8개 테스트를 당시 범위로 표시 |
| [docs/RECEIVABLES_MOBILE_MATCHING.md](RECEIVABLES_MOBILE_MATCHING.md) | 기술 설계 / 작업 기록 | 기능4b 당시 영상 미수행과 이후 제작 자료 링크 구분 |
| [docs/RECEIVABLES_MOBILE_VALIDATION.md](RECEIVABLES_MOBILE_VALIDATION.md) | 작업 기록 | 기능4a 검증을 보존하고 이후4b 구현 연결 |
| [docs/RECEIVABLES_REGISTRATION.md](RECEIVABLES_REGISTRATION.md) | 기술 설계 | 배분·후보·CSV·사용량·모바일을 미구현처럼 보이던 설명 수정 |
| [docs/RECEIVABLES_USAGE.md](RECEIVABLES_USAGE.md) | 기술 설계 | 사용량 화면 구현과 운영 후속 구분 |
| [docs/RECEIVABLES_USAGE_SMOKE.md](RECEIVABLES_USAGE_SMOKE.md) | 작업 기록 | 사용량 단계180개 검증 보존, 이후 모바일 구현 연결 |
| [docs/adr/0001-auth-tenant-ai-boundary.md](adr/0001-auth-tenant-ai-boundary.md) | 의사결정 | 한국어화, 기존 Accepted와 사용자 개별 승인 구분, 제약 보존 |
| [docs/adr/0002-receivables-decisions.md](adr/0002-receivables-decisions.md) | 의사결정 | 신규: 기존 도메인·확정·사용량 판단 근거와 주체를 회고로 기록 |
| [docs/ai-review-ai.md](ai-review-ai.md) | 기술 설계 / 작업 기록 | 과거 연결/키 검증과 현재 실행 차단 구분 |
| [docs/ai-review-code-analysis.md](ai-review-code-analysis.md) | 기술 설계 / 작업 기록 | 초기 분석 범위 명시, 미커밋988a983 확인으로 SHA 요청이 해결됨을 기록 |
| [docs/ai-review-design.md](ai-review-design.md) | 기술 설계 / 작업 기록 | 기존 AI 화면 이력과 현재 미수금 화면 구분 |
| [docs/ai-review-independent-review.md](ai-review-independent-review.md) | 작업 기록 | 당시 인증 미구현과 이후 보안 경계 구현 구분 |
| [docs/ai-review-research.md](ai-review-research.md) | 의사결정 참고 / 작업 기록 | 공식 조사 근거 보존, 조사 당시 구현과 현재·수요 가설 구분 |
| [docs/ai-review-running.md](ai-review-running.md) | 기술 설계 / 작업 기록 | configured=false와 현재 PRO 포함 실행 차단·인증 구현 반영 |
| [docs/architecture.md](architecture.md) | 기술 설계 | 실제 Service→JdbcTemplate 경계·잠금·트랜잭션·성능 한계 추가 |
| [docs/auth-security-review.md](auth-security-review.md) | 작업 기록 | 개발자에게 전달을 구현 담당 에이전트에게 전달로 정정 |
| [docs/demo/README.md](demo/README.md) | 포트폴리오 / 작업 기록 | 유지: 합성 자료·시드·실제 API·재녹화 범위 명확 |
| [docs/legacy-erd.md](legacy-erd.md) | 기술 설계 | 유지: 초기 전표안이며 현재 스키마와 다름이 명시됨 |
| [docs/portfolio/README.md](portfolio/README.md) | 포트폴리오 | 자료 보존, Markdown 변경이 PPT/PDF에 자동 반영되지 않음을 명시 |
| [docs/receivables-allocation-review.md](receivables-allocation-review.md) | 작업 기록 | 기존 수치·날짜·결함·주체 보존, 당시 단계의 후속과 현재 구현 구분 |
| [docs/receivables-domain-plan.md](receivables-domain-plan.md) | 기술 설계 / 의사결정 | 초기 제안과 현재 V9~V13 구현의 차이 명시; 기준 절 링크 갱신 |
| [docs/receivables-erd.md](receivables-erd.md) | 기술 설계 | FK·제약·인덱스 목적과 서비스 합산 검사의 책임 명시 |
| [docs/receivables-import-review.md](receivables-import-review.md) | 작업 기록 | 기존 수치·날짜·결함·주체 보존, 당시 단계의 후속과 현재 구현 구분 |
| [docs/receivables-market-plan.md](receivables-market-plan.md) | 의사결정 참고 | 제품 가설과 실제 화면·읽기 전용 샘플 구현 구분 |
| [docs/receivables-matching-review.md](receivables-matching-review.md) | 작업 기록 | 기존 수치·날짜·결함·주체 보존, 당시 단계의 후속과 현재 구현 구분 |
| [docs/receivables-mobile-core-review.md](receivables-mobile-core-review.md) | 작업 기록 | 기존 수치·날짜·결함·주체 보존, 당시 단계의 후속과 현재 구현 구분 |
| [docs/receivables-mobile-matching-review.md](receivables-mobile-matching-review.md) | 작업 기록 | 유지: 현재 검증 범위·실행 주체·fixture와 실서버 구분 명확 |
| [docs/receivables-mobile-plan.md](receivables-mobile-plan.md) | 기술 설계 / 의사결정 | P0 제안·기능4a 이력과 현재4b 네 탭 구현 분리 |
| [docs/receivables-registration-review.md](receivables-registration-review.md) | 작업 기록 | 기존 수치·날짜·결함·주체 보존, 당시 단계의 후속과 현재 구현 구분 |
| [docs/receivables-usage-review.md](receivables-usage-review.md) | 작업 기록 | 기존 수치·날짜·결함·주체 보존, 당시 단계의 후속과 현재 구현 구분 |
| [mobile/README.md](../mobile/README.md) | 프로젝트 소개 / 기술 설계 | 유지: 현재 연결 흐름·실행·실기기 미검증 일치 |
| [docs/DOCUMENTATION_REVIEW.md](DOCUMENTATION_REVIEW.md) | 작업 기록 | 신규: 전수 목록·수정 이유·검증·확인 필요 항목 |

## 현재 구현과 달랐던 설명

V1~V7까지만 있다는 설명을 현재 소스 V1~V13과 구분했다. 인증 없음·기본 실행 후 legacy API 사용 가능·등록 이후 배분/후보/이용량/모바일 미구현이라는 안내를 정정했다. AI capabilities의 configured는 키 존재 표시가 아니며 현재 서버는 false를 반환한다. 모바일은 기능4b까지 구현했으나 CSV 화면·실기기는 여전히 후속이다.

## 개발자 확인이 필요한 항목

- ADR의 구체 DDL·opaque 세션·부모 잠금·동기 사용량 선택은 에이전트 구현 또는 기존 기록에서 확인된다. 개발자의 직접 비교·승인·최종 검토 증거는 없다. 다음 관련 변경 전에 핵심 코드를 확인하고 유지/변경 판단을 기록한다.
- LEGACY_RECONCILIATION에 남은 회계·정산 실무 경험 1인칭 서술은 사용자 본인의 사실인지 확인이 필요하다. 원문은 보존하고 확인되지 않은 개인 경험을 새 포트폴리오 근거로 사용하지 않았다.
- PPT·PDF는 이번 Markdown 재구성에 맞춰 재생성하지 않았다. 재배포 전 역할·설계 근거를 최신 PORTFOLIO와 대조해야 한다.

## 검증 범위

총괄과 기술 담당이 실제 Controller·Service·SQL·마이그레이션·관련 테스트 메서드를 대조했다. 별도 검토 담당이 README·PORTFOLIO·AGENTS·계획·작업 이력·ADR 0002를 검토했다. 그 과정에서 gradlew 실행 권한과 취소 상태/버전의 파생 설명을 수정했다.

최종 문서 46개의 로컬 링크 284건에서 파일·헤더 누락 0건을 확인했다. `git diff --check`를 통과했고 기존 38개 수정·신규 2개가 모두 Markdown임을 확인했다. AI_REVIEW_WORK의 Objective 이하 단계 이력과 FREE 계획의 P0 검증 이후 원문은 보존 여부를 비교했다. 새 테스트·실제 API·DB 접속·유료 모델 호출은 수행하지 않으며 과거 검증 수치를 이번 결과로 표시하지 않는다.
