# 거래처·청구·입금 직접 등록 독립 검토

> 문서 범위: 등록 기능 단계의 독립 검토 이력이다. 아래 “남은 단계”와 테스트 수치는 당시 기준이며 이후 완료 상태는 [현재 계획](RECEIVABLES_FREE_MVP_PLAN.md)을 따른다. 검증 주체와 근거는 원문대로 보존한다.

2026-11-01. 대상은 인증 기반 다음 기능 단위인 tenant 소유 수동 등록·목록·상세와 additive V9다. 검토자는 관련 코드·스키마·테스트를 읽으며, DB 테스트와 실제 서버 검증은 총괄/구현 담당의 실행 기록과 구분한다. 실제 유료 모델 호출은 하지 않는다.

## 검토 상태

`ReceivablesController`, `ReceivablesService`, `RegistrationInput`, 예외 처리, V9, 신규 통합 테스트 14개와 [API 계약](RECEIVABLES_REGISTRATION.md)의 최종 읽기를 완료했다. 최종 코드에서 남은 P1/P2 결함은 찾지 못했다. `git diff --check`를 통과했다.

## 수정 확인

구현 담당의 자체 검토에서 발견한 거래처 코드 정상화 문제를 재확인했다. 초기 대문자 변환 후 ASCII 검사는 `ß`/`ſ`를 `SS`/`S`로 바꾸어 허용할 수 있었다. 최종 코드는 원래 입력을 ASCII allowlist로 먼저 검사한 뒤 대문자로 저장하며, 두 입력의 400 회귀 case도 추가했다. 코드에서 수정·삭제·배분 기능을 추가하지 않았음을 확인했다.

## 확인한 경계

- 모든 public 서비스 등록·목록·상세 진입점에서 `TenantAccess.require`가 활성 세션과 현재 membership을 검사한다. 등록 재전송에도 같은 검사가 적용된다. tenant URL을 허용하는 기존 필터만으로 소유권을 판단하지 않는다.
- 목록·상세·중복 요청 조회는 tenant 조건을 가진다. 다른 tenant의 리소스 ID와 없는 ID는 같은 404이며, 타 tenant 자체 접근은 기존 403이다. invoice의 거래처 조회와 목록 필터도 tenant 범위다. V9의 `(tenant_id, counterparty_id)` 복합 FK가 교차 연결을 DB에서도 거절한다.
- 필드 allowlist가 소유자·actor·통화·출처와 미구현 상태 필드를 거절한다. actor는 활성 서버 세션, tenant는 권한을 확인한 URL, 생성 시각은 서버에서 정한다. 금액 입력은 JSON 문자열로만 `1..1000000000000` 정수 원을 받고 응답도 문자열이다. 부동소수점 변환·소수 잘라내기가 없다. 날짜는 실제 달력일과 1..9999년을 검증하며 지급기한은 청구일보다 빠를 수 없다.
- `(tenant, operation, Idempotency-Key)` 유니크 행과 resource가 같은 트랜잭션에서 저장된다. 정상화한 입력 record의 SHA-256을 비교하며, 같은 키·같은 입력은 같은 resource, 다른 입력은 409다. `INSERT ON CONFLICT DO NOTHING`이 선행 요청 commit을 기다린 후 다음 조회가 완료 결과를 읽는다. 중복 코드·청구번호 실패는 트랜잭션 밖에서 안전한 409로 변환한다. deferred 복합 FK는 미완료 request가 홀로 commit되지 않게 한다.
- 응답 record는 등록 사실·출처·actor·시각을 제공하며 변경/삭제 API는 없다. 저장 예외는 SQL·금융 입력·constraint 이름을 로그나 응답에 노출하지 않는 503으로 변환한다. API는 기존 no-store 보호를 받는다.
- 목록은 크기 1..100, 0 이상 page, `created_at DESC, id DESC`로 정렬한다. 같은 시각에도 순서가 결정된다. offset 방식이므로 페이지 사이에 새 행이 등록되면 조회 결과가 이동할 수 있으며, 고정 snapshot을 보장하는 계약은 아니다.
- V9는 새로운 수동 등록 테이블·인덱스만 추가한다. 기존 vendor/business_event/bank/upload 행을 tenant에 연결하거나 기존 원본 ID를 신규 식별자로 재사용하지 않는다. 금융 데이터의 cascade 삭제는 추가하지 않는다.

## 읽어 확인한 검증 코드

신규 통합 테스트 14개는 실제 bearer/security filter를 사용한다. 세 리소스의 미인증·타 tenant·타 리소스 ID 접근, 원화 문자열 상한과 잘못된 JSON 숫자/소수/부호, 실제 날짜와 지급기한, 필드 allowlist, tenant·operation별 키 범위, 정렬 tie와 페이지 상한을 검사한다. 동시 같은 키/같은 payload는 같은 응답·한 행, 다른 payload는 201/409를 검사한다. 다른 키의 같은 청구번호 경쟁, 후반 DB 오류의 request/resource rollback과 재시도, SQL 직접 삽입의 교차 tenant FK 거부도 포함한다. 폐기된 세션의 직접 서비스 호출은 신규 public 진입점 아홉 개에서 401을 검사한다.

매 테스트 종료 시 해당 테스트가 만든 tenant/account만 대상으로 request→금융 resource→identity 순서로 정리한다. 기존 auth 테스트가 tenant를 삭제할 때 신규 금융 FK가 남는 문제를 production cascade 없이 방지한다. 실패를 유도하는 CHECK도 테스트 DB 격리가드와 `finally` 제거를 사용한다. 각 테스트 후 AI client와 기존 전역 query의 무호출을 검사한다. 이는 검증 코드 검토 결과이며 독립 검토자가 DB 테스트를 실행한 결과는 아니다.

총괄이 실행한 `/workspace/.local/run/registration-full-build.log`의 `BUILD SUCCESSFUL in 47s`와 테스트 XML 17개의 합계 **118개, 실패·오류·스킵 0**을 검토자가 읽어 확인했다. 신규 14개와 기존 104개 회귀가 함께 통과했다. 독립 검토자는 중복 DB 테스트·실제 모델 호출·HTTP 서버/브라우저 검증을 실행하지 않았다.

## 범위와 남은 출시 과제

등록은 배분 확정·취소·감사 원장, 미수·연체 잔액 계산, 규칙 후보, CSV import, 성공 대사 quota를 구현하지 않는다. 이 응답을 입금 확인이나 완납 증거로 해석하지 않는다. 새 코드에는 AI/provider·legacy 전역 금융 서비스 호출이 없다.

공개 출시 전에는 기존 [인증 검토](auth-security-review.md)의 계정 보호·운영 과제와 함께 금융 자료의 보존/삭제, 요청·저장량 제한, 후속 배분 불변식·감사와 모바일 연결을 별도 검증해야 한다. 이번 검토는 공개 출시 승인이 아니다.
