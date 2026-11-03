# ADR 0002: 미수금 도메인·확정·사용량의 결정 근거

정리일: 2026-11-02 (Asia/Seoul). 새 설계 승인 기록이 아니라 기존 사용자 지시, 구현과 2026-11-01 검증 기록을 대조한 회고다. 개발자의 직접 판단이 확인되지 않는 세부 설계를 사후 승인으로 간주하지 않는다.

## 1. 기존 대사 데이터와 미수금 도메인 분리

- **문제:** `business_event`의 예정 현금흐름과 `daily_review`의 날짜별 검토는 고객 채권·지급기한·개별 지급 여부를 나타내지 않는다.
- **대안:** 기존 이벤트·거래처를 재사용할 수 있는지 검토하고, 의미가 다르면 별도 모델을 추가한다는 사용자 요구가 있었다. 기존 이벤트를 청구로 변환하는 마이그레이션은 채택하지 않았다.
- **선택·주체:** 기존 도메인의 의미·원본 보존은 사용자 명시 지시다. 별도 tenant 소유 거래처·청구·입금과 V9~V10 배분 모델은 에이전트 설계·구현에서 확인된다. 구체적인 테이블·키 선택에 대한 사용자 개별 승인 기록은 확인되지 않는다.
- **이유·비용:** 기존 합계 API를 유지하면서 청구별 잔액을 계산할 수 있다. 대신 전역 거래처와 신규 거래처가 분리되고, 기존 자료 자동 이관은 지원하지 않는다.
- **구현·검증:** [도메인 계획](../receivables-domain-plan.md), [ERD](../receivables-erd.md), [등록 검토](../receivables-registration-review.md). V9의 tenant FK와 등록 접근 제어·동시 요청 검증을 포함한 당시 전체 118개 테스트 기록이 있다.

## 2. 후보와 확정을 분리하고 배분 이력을 보존

- **문제:** 같은 날짜·금액·이름으로 거래를 확정하면 동명이인·부분 입금·합산 입금을 잘못 연결할 수 있다. 두 요청이 같은 잔액을 동시에 소비할 수도 있다.
- **대안:** 사용자 요구는 규칙 후보 생성 뒤 사람이 검토·확정하는 흐름이다. 자동 확정은 채택 범위에서 제외됐다. 낙관적 잠금 등 다른 동시성 기법을 당시 비교했다는 기록은 없어 검토한 대안으로 쓰지 않는다.
- **선택·주체:** 사용자 확정과 Java·DB 금액 처리, 취소·감사 필요성은 사용자 지시다. 다대다 배분 line, 부모 행 잠금 순서, READ_COMMITTED 트랜잭션은 에이전트 설계·구현에서 확인된다.
- **이유·비용:** 확정 시점의 현재 잔액으로 재검사하고, 취소해도 원래 배분과 사유를 보존한다. 사용자의 확인 단계가 필요하며 같은 부모를 수정하는 요청은 잠금 대기할 수 있다. 처리량·잠금 성능은 측정하지 않았다.
- **구현:** `AllocationController` → `AllocationService.confirm` → `reserve` → `lockAndValidate`/`lockParents` → `checkAvailable` → 배분·감사 저장. 입금 UUID 정렬 후 청구 UUID 정렬로 `FOR UPDATE`한다. 행 간 합산 상한은 DB CHECK 하나로 보장하지 않으며, 잠금 후 SQL 합계와 Java 재검사로 보장한다. 취소도 같은 부모 잠금 규칙을 사용한다. [API·트랜잭션 계약](../RECEIVABLES_ALLOCATION.md)
- **검증:** `AllocationIntegrationTest`의 `competingInvoicesCannotOverAllocateOnePayment`, `competingPaymentsCannotOverAllocateOneInvoice`, `opposingParentOrdersLockConsistentlyForConfirmationAndCancellation`, `lateAuditFailureRollsBackConfirmationAndCancellationAndSameKeysCanRetry`. 당시 전체 135개 테스트 기록과 [독립 검토](../receivables-allocation-review.md)를 근거로 한다.

## 3. 사용량을 후보 결과와 한 트랜잭션으로 저장

- **문제:** 결과 저장과 이용량 차감이 따로 성공하면 실패한 작업을 과금하거나 같은 실행을 중복 차감할 수 있다.
- **기록된 대안:** 초기 계획의 비동기 예약·lease/fencing 방식과, 현재 제한된 동기 후보 실행의 단일 트랜잭션 방식. 전자는 작업 기록에서 후자로 대체됐다고 명시돼 있다.
- **선택·주체:** 성공한 처리만 집계하고 실패·중복을 차감하지 않는 기준은 사용자 지시다. 동기식 구현 채택은 에이전트 작업 기록에서 확인되며, 별도 사용자 선택·승인은 확인되지 않는다.
- **이유·비용:** 결과·사용량 event·월 counter를 함께 commit하거나 rollback해 영구 예약 복구 없이 일관성을 유지한다. 월 행이 직렬화 지점이며 실행 시간·조회량에 제한을 둔다. 비동기 대량 처리로 확장할 때는 재설계가 필요하다.
- **구현·검증:** `MatchingService.create`와 `UsageService`, V13. `UsageIntegrationTest.concurrentDifferentKeysCompeteForLastSlotWithoutOvershoot`, `snapshotLedgerAndCounterFailuresRollbackEverythingAndPermitSameKeyRetry`, `monthChangeWhileWaitingRollsBackAndSameKeyRetriesInNewMonth`. 당시 전체 180개 테스트 기록 및 [사용량 계약](../RECEIVABLES_USAGE.md), [독립 검토](../receivables-usage-review.md).

## 개발자 확인과 다음 변경

위 업무 기준은 대화에서 확인되지만 세부 구현을 개발자가 직접 읽고 승인·검증했다는 기록은 없다. 현재 문서 정리는 구현을 바꾸거나 기존 선택을 새로 승인하지 않는다. 다음 관련 기능 변경 전에 개발자가 핵심 코드·데이터 흐름·트레이드오프를 확인하고, 유지 또는 변경 결정과 날짜를 이 ADR에 남긴다. 새로운 주요 DB·아키텍처 변경은 [AGENTS.md](../../AGENTS.md)의 승인 절차를 따른다.
