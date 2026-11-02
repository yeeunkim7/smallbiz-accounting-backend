# 모바일 core 독립 검토

검토일: 2026-11-01 (UTC). 대상: feature4a Expo core. 최종 소스와 실제 Chromium 렌더를 검토했고 이번 core 범위에서 남은 차단 결함은 찾지 못했다. iPhone/Android 실기기·네이티브 빌드·스토어 배포 검증은 수행하지 않았다.

## 범위와 근거

`AGENTS.md`, `receivables-mobile-plan.md`, `receivables-market-plan.md`의 기존 제품 경계와 시각 원칙을 재사용했다. 신규 시장 조사나 외부 디자인 사이트 조회는 하지 않았으므로 이번에 검사한 외부 URL은 없다. 앱의 주 금액 → 보조 잔액 → 기록/조회 행동 → 최근 청구 순서는 업무 우선순위를 읽기 쉽게 만든다. 상태는 색상과 문구를 함께 쓰고, 샘플 표시와 연체 기준일이 유지된다. 다른 제품 화면을 복제한 근거는 없다.

Core의 실제 범위는 인증/회원가입, 서버 미수 요약, 거래처·청구·입금 직접 등록, 페이지 장부, 청구 잔액 상세, 서버 이용량/로그아웃과 로컬 읽기 전용 샘플이다. 후보 검토·할당 확정/취소와 CSV는 다음 단계로 명시하며 실행되지 않는 버튼을 만들지 않았다. FREE 업무에서 AI 경로가 없다.

검토한 소스: `mobile/App.tsx`, `src/{api,commands,data,format,forms,sample,screens,session,types,ui,vault}`, `tests/critical.test.ts`, 앱 실행 문서와 backend의 인증/등록/잔액/이용량 계약 및 CORS 설정/회귀 시나리오. 서버의 `asOf`는 현재 전체 장부의 연체 판정일이고 과거 잔액 재현 기능이 아니다. 화면의 현재 잔액·전체 장부·연체 기준일 문구가 이 계약을 반영한다.

## 발견과 수정 확인

| 발견 | 수정 및 확인 |
| --- | --- |
| 브라우저 login이 API 요청 전에 실패 | raw `fetch`를 객체 메서드로 호출하면 Chromium에서 `Illegal invocation`이 실제 발생했다. 기본 fetcher를 lexical wrapper로 바꾼 후 인증 fixture의 요청/화면 전환이 동작했고, 해당 receiver 회귀 검사도 추가됐다. |
| 불확실한 금융 POST 후 공백만 바꿔도 동일 정규화 payload의 key가 바뀜 | raw 입력 변화에서 key 초기화를 제거했다. 정규화 payload가 key를 결정한다. root의 실제 서버 commit 뒤 503 응답 fixture → 공백 수정 → 재시도 기록에서 같은 key와 입금 1건을 확인했다. |
| 등록 중 화면을 떠나면 늦은 성공 callback이 새 초안/탐색을 바꿀 수 있음 | root가 발견한 경합이다. 전송 중 입력·탭·뒤로 가기/Android Back을 잠그는 소스를 확인했다. 실제 Android Back은 기기 미검증이다. |
| 320px 샘플의 보조 금액/헤더가 불편하게 줄바꿈됨 | 보조 지표 세로 배치와 짧은 샘플 헤더로 수정했다. 최종 320px 샘플을 직접 다시 열어 확인했다. |

보정 전 320px에서 최대 단건 1조 원과 큰 합계의 숫자 묶음이 줄 중간에서 갈라지는 문제를 발견했다. 최종 `MainMoney`는 사용할 폭과 형식 문자열 길이에 맞춰 표시 크기를 정하고, 매우 큰 값/글자 확대 시에는 전체 수를 가로 스크롤로 읽을 수 있게 한다. 최종 320px 화면에서 `1,000,000,000,000원`과 `9,007,199,254,740,993원`이 각각 한 줄로 정확히 보이는 것을 직접 확인했다. 반올림·생략·가로 문서 넘침이 없다. 큰 보조 지표는 숫자 묶음을 유지하고 원 단위를 다음 줄에 표시할 수 있다. `UNPAID` 문구도 은행 입금 부재를 추정하지 않는 `입금 미연결`로 확인했다.

## 기능·경계 검토

- 금액은 decimal string을 `BigInt`로 표시/입력 범위 검증만 한다. 합계·배분·미수 계산과 정책 권한은 서버에 있다. 미연결 입금이 미수를 줄였다고 표시하지 않는다. 날짜는 `Asia/Seoul`의 오늘과 엄격한 윤년 검증을 사용하고 날짜 전용 문자열을 UTC로 옮기지 않는다.
- 요청마다 12초 timeout이 있고 자동 재시도/전체 페이지 수집이 없다. 목록은 페이지 최대 10건, 홈은 최근 3건의 잔액/고객만 조회한다. 오류와 로딩을 빈 목록으로 바꾸지 않는다. 등록 결과 불명 안내와 같은 payload/key 재시도, 가입 결과 불명 시 로그인 확인 안내가 있다. 원문 서버 오류/stack을 표시하지 않는다.
- native bearer는 SecureStore의 기기 전용 잠금 접근 정책을 사용한다. 저장 실패는 로그인 완료로 처리하지 않고 발급 세션 폐기/로컬 삭제를 시도한다. Web은 메모리만 사용한다. session epoch는 이전 세션의 성공/401을 버리고, 현재 401은 보호 화면을 지우고 만료 안내를 보인다. 계정/사업장별 workspace 재생성으로 초안·key·조회 상태를 분리한다. 늦은 SecureStore 쓰기 뒤 로그아웃 삭제 순서도 직렬화한다. native 실행 증거와는 구분한다.
- 샘플의 summary/목록/상세/이용량은 고정 합성 값이고 API, 실제 자료 복사, 등록, 실제 이용량 차감을 사용하지 않는다. 화면에 읽기 전용/합성 표시가 유지되고 이용량은 샘플 수치로 설명한다. 실제 계정의 한도는 서버 응답이며 앱 정책 상수가 없다.
- CORS는 빈 기본 설정, 정확한 HTTP(S) origin/port, 제한된 method/header, cookie credentials 미허용이며 기존 인증을 유지한다. 여섯 회귀 시나리오를 소스 검토했다. 독립 검토자는 Java/DB suite를 실행하지 않았다.

## 실행·화면 증거

독립 검토자는 `/usr/bin/chromium` + Python Playwright로 실제 `http://127.0.0.1:8081`을 열었다. sample 홈 → 장부 → 청구 상세 → 계정 → 종료를 390×844/320×740 viewport에서 실행했다. API 0회, page error 0개, 320px document width 320px, 당시 보이는 button/tab의 44×44px 미만 대상 0개였다. 역할/이름을 이용한 조작, 선택 상태, 포커스 테두리를 확인했다. 최종 수정 후 320px 샘플을 다시 실행하여 API 0회와 document width 320px, 헤더/지표의 읽기 쉬운 배치를 확인했다.

별도 synthetic route fixture는 인증 → 9,007,199,254,740,993원 합계 → 단건 1조 원 상세/긴 고객명 → 현재 401을 실행했다. 해당 값은 실제 tenant 데이터가 아니다. 금액 원문 보존, 가로 넘침 없음, 만료 배너와 보호 사업장 제거, page error 0개를 확인했다. 실행 산출물은 `/tmp/mobile-core-review-{sample390,sample320,detail390,account390,large320,large-detail320,expired320}.png` 및 `sample320-final.png`이며 직접 이미지를 열어 확인했다. fixture는 금융 계산/실서버 동작을 증명하지 않는다.

root의 실제 서버 연결 브라우저 log/script(`/workspace/.local/run/mobile-browser.log`, 최종 재실행 가능한 `scripts/verify-receivables-mobile-browser.py`)도 검사했다. 저장된 script는 local HTTP origin과 `reconciliation_test` DB 설정을 요구하고 credential 값을 출력하지 않으며 합성 tenant 정리를 수행한다. 9개 시나리오는 실제 가입/거래처·청구·입금, 불확실한 응답 재시도, 미연결 입금과 미수 분리, 이용량 0회 유지, Web storage key 0개/새로고침 로그인, 서버 세션 폐기 후 화면 잠금을 포함하고 page error는 0개였다. [실제 홈](screenshots/mobile-core-live-home.png), [입력](screenshots/mobile-core-registration.png), [이용량](screenshots/mobile-core-account.png), [desktop](screenshots/mobile-core-desktop.png)을 직접 열어 보았다. 해당 테스트 tenant는 합성 자료이며 정리 완료 기록이 있다.

독립 실행한 `npm test`: 8개 통과, 실패/skip 0개. UI 구현을 복제하는 검사 대신 key 재시도, 세션 경합, 안전 저장 쓰기 순서, URL, fetch receiver, 금액/날짜 경계를 검사한다.

## 남은 검증과 다음 단계

브라우저 mobile viewport는 iOS/Android 앱 검증을 대신하지 않는다. VoiceOver/TalkBack, Dynamic Type/OS 글자 확대, native 키보드 회피, SecureStore 실제 읽기/복구/저장 실패, safe area, iPhone/Android 설치/서명, Windows 개발 절차 실행은 후속 게이트다. 브라우저에서 확인한 폰트/색/44px 대상만으로 네이티브 접근성 완료를 주장하지 않는다. 더 많은 실제 페이지를 넘기는 장부/거래처 선택과 복수 사업장 전환의 렌더 시나리오도 후속 확인 대상이다(페이지/격리 로직은 소스 검사).

후속 기능은 후보 검토·사람 확정/취소·CSV이며 이번 core 완성 또는 native 준비와 혼동하지 않는다. 실제 모델 호출, 유료 API, 공개 배포, EAS/스토어 제출은 수행하지 않았다.
