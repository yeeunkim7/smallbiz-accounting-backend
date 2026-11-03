# 규칙 후보·저장 결과 독립 검토

> 문서 범위: 후보 기능 단계의 독립 검토 이력이다. 아래 “남은 단계”와 테스트 수치는 당시 기준이며 이후 완료 상태는 [현재 계획](RECEIVABLES_FREE_MVP_PLAN.md)을 따른다. 검증 주체와 근거는 원문대로 보존한다.

2026-11-01. 대상은 `0e650e3` 다음의 matching package와 additive V11이다. 등록·배분 검토는 기존 문서로 보존한다. 코드·테스트·실행 산출물을 읽으며 중복 DB 테스트·유료 모델 호출은 하지 않는다.

## 상태

matching 서비스·controller·policy·V11, 신규 통합 테스트 14개와 `RECEIVABLES_MATCHING.md` API 문서를 현재 작업본 기준으로 확인했다. 이름만 일치하고 잔액이 다른 후보의 명시 경고가 빠진 점을 전달했고, `REMAINING_AMOUNT_MISMATCH` 경고와 회귀 case 추가를 확인했다. 완납/취소 테스트 안의 저장 불변식 위반 거부 case도 확인했다. 현재 미해결 결함은 발견하지 못했다.

## 확인한 경계

- create/get/list 모두 현재 세션·tenant membership을 확인한다. 조회·재전송·원천 SQL은 tenant 범위다. actor·규칙 버전·시각은 서버가 정하며 요청 필드와 날짜를 엄격히 검증한다. 기간은 양끝 포함 최대 366일이다.
- payment는 booked date 범위, invoice는 issue date가 종료일 이하인 현재 미수 자료를 사용한다. 시작일보다 오래된 미수 청구도 포함한다. 활성 배분은 cancellation을 제외하며 두 원천·잔액·증빙을 한 SQL snapshot으로 읽는다. `allocated <> amount` 행을 읽고 음수·원금 초과를 명시 오류로 거절한다.
- 원천별 상한+1을 조회해 초과 시 422로 전체 실패시킨다. 기본값은 각 200개와 payment당 후보 5개이며 가격/월 quota가 아닌 운영 계산 한도다. policy는 안전 범위 밖 설정을 startup에서 거절한다. 원천을 몰래 자른 성공 결과는 없다. 후보 top 제한은 전체 후보 수·truncated flag로 표시한다.
- 현재 미배분/미수 금액 일치 또는 비어 있지 않은 정규화 이름 일치만 후보를 만든다. 점수→지급기한 거리→invoice UUID 순으로 정렬한다. 금액은 문자열이고 이름·날짜의 불일치/누락을 근거와 경고로 표시한다. 추천 금액은 가용액의 최솟값이며 복잡한 부분합·묶음 탐색이나 자동 배분은 하지 않는다.
- 같은 tenant/key INSERT 충돌은 선행 commit을 기다린다. 정규화 요청 hash가 같으면 저장 결과를 재생하고 다르면 409다. source 읽기와 SUCCESS JSON 저장이 같은 READ COMMITTED 트랜잭션이며 실패 시 run도 rollback된다. GET은 현재 자료로 다시 계산하지 않고 원래 증빙·규칙 버전·결과를 반환한다.
- 결과는 생성 당시 현재 상태의 snapshot이다. 이후 배분·취소로 낡을 수 있으며 기존 allocation API가 현재 가용액을 잠금 아래 다시 검증해야 한다. 후보 0건은 입금 누락의 증거가 아니다. 신규 코드에는 allocation/감사/원본 변경·AI/provider·legacy 전역 금융 query·usage/quota 호출이 없다.

## 검증 코드

실제 security filter를 사용하는 테스트는 금액/이름 후보와 경고, Unicode 정규화와 원문 증빙, 지급일 범위·오래된 청구·기한 경고, 취소 전후 source eligibility, 결정적 정렬과 top 제한 표시, source 상한 전체 거부, 날짜·필드·설정 오류, 모든 route/direct service 권한, 같은/다른 hash 병렬 재전송, snapshot 저장 실패 rollback, 배분 후 기존 결과 불변과 낡은 추천의 allocation 재검증을 포함한다. 각 테스트는 자기 tenant만 FK 순서로 정리하고 AI/client·기존 전역 query 무호출을 검사한다. 검토자가 테스트를 중복 실행한 것은 아니다.

최종 실행 산출물도 직접 읽었다. `backend-java/build/test-results/test/TEST-*.xml` 19개를 합산해 전체 149개와 matching 14개 모두 실패·오류·skip 0임을 확인했다. `/workspace/.local/run/matching-full-build.log`는 `BUILD SUCCESSFUL in 1m 16s`, `8 actionable tasks: 8 executed`를 기록한다. 이 기록은 전체 회귀와 패키징 검증이며 개발 DB 적용·HTTP smoke·공개 배포 검증을 뜻하지 않는다.

## 남은 단계

월별 성공 대사 quota·예약/복구는 다음 기능이며 이번 run에는 적용하지 않았다. 현재는 내부 사전 출시 단계로 기록하고 FREE 공개 출시 완료나 무제한 실행을 약속하지 않는다. tenant CSV import, 모바일 연결, 운영 요청/저장량 제한과 금융 보존/삭제 정책도 남아 있다. 이번 검토는 공개 출시 승인이 아니다.
