# 수동 배분·취소·잔액 독립 검토

> 문서 범위: 배분 기능 단계의 독립 검토 이력이다. 아래 “남은 단계”와 테스트 수치는 당시 기준이며 이후 완료 상태는 [현재 계획](RECEIVABLES_FREE_MVP_PLAN.md)을 따른다. 검증 주체와 근거는 원문대로 보존한다.

2026-11-01. 대상은 직접 등록 다음 기능 단위인 tenant 소유 수동 배분·취소·감사·현재 잔액과 additive V10이다. [직접 등록 검토](receivables-registration-review.md)는 별도 기록으로 보존한다. 검토자는 관련 코드·스키마·테스트를 읽고, DB 테스트는 총괄/구현 담당의 실행 기록과 구분한다. 실제 모델 호출은 하지 않는다.

## 검토 상태

`AllocationService`, controller, 입력 검증, V10, 공유 검증 helper 추출, 신규 통합 테스트 17개와 [API 계약](RECEIVABLES_ALLOCATION.md)의 최종 읽기를 완료했다. 남은 P1/P2 소유권·잔액·동시성 결함은 찾지 못했다. `git diff --check`를 통과했다.

## 확인한 경계

- 서비스 public 진입점 일곱 개에서 활성 세션과 tenant membership을 재검증한다. command 재전송도 권한 검사를 거친다. 배분 대상 payment/invoice, batch, cancellation, audit와 command 조회는 tenant 범위이며 V10의 복합 FK가 다른 tenant 부모 연결을 DB에서도 거부한다.
- confirm은 1..100개 line과 중복 없는 payment/invoice 쌍을 요구한다. UUID·필드 allowlist·정수 원 문자열 `1..1000000000000`을 검증하고 line 순서를 정규화한다. 서버가 actor·시각·상태를 정한다. 기존 등록 금액·기한·작성자와 응답 DTO는 변경하지 않는다.
- confirm과 cancel은 command를 확보한 다음 payment UUID 문자열 오름차순, invoice UUID 문자열 오름차순으로 모든 부모 행을 잠근다. cancel은 불변 line으로 부모 집합을 찾고 동일 순서로 잠근 뒤 cancellation 존재를 다시 읽는다. 먼저 batch 상태를 잠그는 역순 경로는 없다.
- confirm은 모든 부모 잠금 이후 현재 활성 배분 합계를 다시 조회해 입금·청구 양쪽 가용액을 검사한다. 한 명령의 같은 부모별 요청금액도 합산한다. cancel은 같은 부모 잠금을 공유하므로 새 confirm과 교차해 잔액을 초과하지 않는다. 배분/취소 중 원래 금융 행이나 ledger line의 UPDATE/DELETE는 없다.
- batch/line은 확정 사실로 보존하고 별도의 유일한 cancellation 사실로 취소 상태를 파생한다. audit는 batch별 CONFIRMED/CANCELLED 각각 한 개로 제한하며 actor·사유·시각을 남긴다. command→audit FK는 deferred이고 결과·line·audit가 한 트랜잭션으로 commit/rollback된다.
- command의 키는 tenant·operation 범위이며 정규화한 payload hash를 비교한다. 완료 결과 JSON을 audit에 보존하므로 confirm 후 취소되어도 confirm 재전송은 최초 version 1 응답을 반환한다. cancel 재전송도 최초 취소 actor·시각·감사를 보존한다. 다른 키의 재취소는 명시적 409다.
- balance와 summary는 단일 SQL snapshot의 현재 활성 배분을 사용한다. detail/list는 읽기 전용 REPEATABLE READ에서 상태·line·audit를 함께 읽는다. 합계는 PostgreSQL numeric과 Java BigInteger로 보존하고 금액 응답은 문자열이다. 비정상 초과 잔액을 음수로 표시하지 않고 안전한 오류로 거절한다.
- `asOf`는 현재 잔액에 적용하는 연체 기준일이다. 지급기한 당일은 연체가 아니며, 과거 기준일을 주어도 과거 당시 배분·잔액을 복원하지 않는다. summary의 `allocationState: CURRENT`가 이 경계를 드러낸다. 초과 입금은 미배분 입금으로 남는다.
- 신규 경로는 AI/provider, legacy 전역 금융 query 또는 quota를 호출하지 않는다. V10은 기존 데이터 재배정·수정·cascade 삭제 없이 신규 원장 테이블·인덱스만 추가한다.

## 읽어 확인한 검증 코드

신규 통합 테스트 17개는 실제 security filter를 사용한다. 부분 입금·여러 입금의 한 청구 완납·한 입금의 여러 청구 배분, 청구/입금 양쪽 초과와 같은 명령 내부 합산 초과, due-date 당일/다음 날, 미배분 입금과 빈 데이터, 취소 후 잔액 복원, 정수 문자열과 입력 상한을 검사한다. 정규화한 line 순서 재전송과 취소 후 최초 confirm 응답 보존, 같은 key/상이 payload, 모든 HTTP 경로의 tenant 경계, SQL 직접 교차 tenant FK 거절, 직접 서비스 진입점 일곱 개의 폐기 세션 거부도 포함한다.

동시성 검증은 같은 confirm/cancel의 ledger·감사 1회, 다른 key의 동일 batch 취소 경쟁, 공통 입금/공통 청구의 초과 배분 경쟁, 반대 순서의 부모를 포함한 confirm/cancel, 취소와 신규 confirm의 가용액 경쟁을 포함한다. 후반 audit INSERT 실패를 유도해 confirm의 line/batch/command와 cancel의 취소 사실이 함께 rollback되고 같은 key로 재시도되는지 검사한다. 임시 CHECK는 격리된 테스트 DB에서 `finally`로 제거한다. cleanup은 해당 테스트 tenant의 command→audit→취소→line→batch→등록→identity 순서로만 삭제한다. 테스트마다 AI client와 기존 전역 query의 무호출도 검사한다.

이는 테스트 코드를 읽은 결과다. 독립 검토자는 중복 DB 테스트나 실제 HTTP 서버·브라우저 검증을 실행하지 않았다.

구현 담당은 신규 배분 17개와 기존 등록 14개를 함께 실행해 총 31개, 실패·오류·스킵 0과 `BUILD SUCCESSFUL in 38s`를 보고했다. 이어 총괄이 실행한 `/workspace/.local/run/allocation-full-build.log`의 `BUILD SUCCESSFUL in 1m 1s`, check/build 완료와 테스트 XML 18개의 합계 **135개, 실패·오류·스킵 0**을 검토자가 읽어 확인했다. 신규 17개와 기존 118개 회귀가 함께 통과했다. 이는 독립 검토자의 중복 테스트 실행이 아니다.

## 범위와 남은 출시 과제

이번 범위에는 규칙 후보, tenant CSV import, 성공 대사 usage/quota, 수정·대손·환불·분쟁, 과거 시점 원장 재현, 모바일 연결이 없다. 은행 거래의 부재를 입금 누락으로 단정하거나 수동 등록을 은행 확인 증거로 표시하지 않는다.

공개 출시 전에는 [인증 검토](auth-security-review.md)의 계정 보호와 운영 과제, 금융 보존/삭제 정책, 요청·저장량 제한, 운영 DB 권한과 원장 변경 통제, 사용자 취소 사유·오류·연체 UX를 검증해야 한다. 이 문서는 공개 출시 승인이 아니다.
