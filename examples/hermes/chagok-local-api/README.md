# 차곡 로컬 API 조회 도구

Hermes v0.22.6용 학습 플러그인. 고정 합성 응답을 대신해 기존 Java GET `/api/v1/tenants/{id}/receivables/summary?asOf=...`를 읽는다. 예제 코드는 에이전트가 작성했다. 사용자 PC에서 실제 Java 조회와 Hermes 도구 호출을 확인했다. 실행 근거는 아래에 구분해 기록했다.

## 입력과 권한

도구 이름은 `chagok_receivables_summary`, toolset은 `chagok_local_api`다. 모델 입력은 `as_of`만 받는다. 이 값은 현재 원장의 **연체 판정 기준일**이며 하루 거래 필터나 과거 시점 잔액이 아니다. `CURRENT_LEDGER`와 API 경로를 근거로 반환한다. 개별 거래 원본은 이 도구로 조회하지 않는다.

- 주소는 `http://127.0.0.1:8080`에 고정한다. 환경 프록시·HTTP 리다이렉트는 사용하지 않는다.
- 사용자 홈 `~/.config/chagok-lab/session.json`의 tenant_id와 access_token을 사용한다. 모델이 URL·사업체·인증 정보를 바꿀 수 없다.
- GET만 실행하며 자동 재시도하지 않는다. socket timeout10초, 응답64KiB 상한이다. 전체 작업 시간의 절대 상한을 의미하지 않는다.
- 반환 필드를 제한하고 토큰·서버 오류 본문을 모델에 보내지 않는다. 금액은 Java의 십진 문자열 그대로다.
- 청구·입금 count가 모두0일 때만 NO_DATA다. 잔액0이지만 자료가 있는 경우는 DATA다. 401/403/연결·응답 오류는 NO_DATA와 구분한다.

이 세션 자체는 서버에서 쓰기 권한도 가진다. 플러그인의 GET 제한은 읽기 전용 서버 자격증명이나 OS sandbox와 동일하지 않다. 합성 계정의 로컬 실습 범위이며 FREE 앱에 AI 기능을 개방하거나 기존 PRO 경계를 우회하는 서버 변경은 없다. 실제 배포 시 권한·등급·토큰 scope는 별도 설계·승인 대상이다.

## 설치와 검사

서버와 합성 자료를 먼저 준비한다. 인증 파일은 저장소에 넣거나 내용을 출력하지 않는다. 플러그인 폴더를 `~/.hermes/plugins/chagok-local-api`에 두고 실행한다.

```bash
hermes plugins doctor ~/.hermes/plugins/chagok-local-api --ci
python3 -m unittest discover -s ~/.hermes/plugins/chagok-local-api -p 'test_*.py' -v
```

단위 검사는 HTTP를 모의 처리하므로 모델 호출·실제 서버 변경이 없다. 통과 후 활성화한다.

```bash
hermes plugins enable chagok-local-api
```

기존 Gateway를 새로 읽혀야 한다면 포그라운드 실행 탭에서 Ctrl+C 후 `hermes gateway run`을 실행한다. 새 대화는 다음처럼 도구를 한정한다.

```bash
hermes chat --toolsets chagok_local_api --max-turns 5
```

`--max-turns`는 Hermes의 턴 제한이며 provider 내부 재시도까지 포함한 API 요청 수·비용 상한을 검증한 값은 아니다. 자동 테스트는 실제 모델을 호출하지 않는다.

실습 질문: “chagok_receivables_summary로 as_of=2026-11-04의 현재 원장 요약을 조회해줘. 합성 실습 자료임을 밝히고 서버 금액·건수·조회 근거를 설명해줘. 하루 거래나 과거 잔액으로 해석하지 마.”

## 검증 상태

2026-11-04 총괄 환경에서 오프라인 단위10개 통과. 주소/GET 고정, 날짜 오류, 세션 누락, 인증·접속 오류, 리다이렉트 거부, 크기·응답 타입, 반환 허용목록, 자료 없음과 잔액0 구분, 등록 스키마를 검사했다. 별도 검토 에이전트의 코드 검토에서 차단 결함은 발견되지 않았다. 이 검사는 클라우드 실행이며 사용자 PC의 통합 결과와 구분한다.

같은 날 사용자 PC에서 직접 lookup 호출로 DATA·미수 60,000원과 Java API 경로를 확인했다. 이후 사용자가 Hermes의 실제 도구 실행 표시와 서버 응답에 근거한 설명을 확인했다. 잘못된 날짜 직접 검사 및 모의 401·연결 실패 처리도 통과했다고 보고했다. 정상 모델 경로는 실제 호출, 두 오류 경로는 모의 함수 검사다. 사용자 제공 출력·확인에 근거하며 PRO 제품 출시·개별 거래 원본 조회·엄격한 요청 비용 상한 검증은 아니다. [상세 기록](../../../docs/PORTFOLIO_LEARNING.md)

사용자가 처음 복사한 플러그인은 manifest와 __init__.py 두 파일이다. 위 unittest 명령은 이 저장소의 test_local_api.py까지 복사한 경우에만 사용한다. 테스트 0개 출력은 검증 통과로 보지 않는다.
