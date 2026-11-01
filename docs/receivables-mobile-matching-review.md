# 모바일 입금 연결 독립 검토

검토일: 2026-11-01 (UTC). 대상: 기능 4b의 Expo 입금 연결 화면과 기존 Java API 재사용. 최종 소스, 독립 Chromium fixture, 총괄 담당의 실제 서버 실행 기록을 검토했으며 현재 범위에서 남은 P1/P2 결함은 발견하지 못했다. iOS/Android 네이티브 앱 검증은 수행하지 않았다.

## 범위와 근거

`AGENTS.md`, [모바일 계획](receivables-mobile-plan.md), 기존 core·matching·allocation·usage 검토와 API 계약을 재사용했다. 신규 시장 조사나 외부 디자인 사이트 조회는 하지 않았다. 실제 검토 소스는 `mobile/App.tsx`, `src/{matching,matching-screen,api,commands,data,session,types,screens,ui}`, 핵심 단위 검사와 브라우저 검증 스크립트다. Java의 후보 source/DTO, 배분 재생·현재 상태·잔액 계약, CSV 원본 조회 계약도 대조했다. 모바일·백엔드 코드를 직접 수정하거나 DB suite를 중복 실행하지 않았다.

네 탭 중 `입금 연결`은 날짜 범위 선택 → 성공 저장 시 1회인 새 후보 생성 → 저장 당시 사실·근거·경고 검토 → 부분/묶음 초안 → 현재 잔액 조회 → 명시적 확정으로 이어진다. 연결 이력에서 사유를 입력하고 별도 확인한 뒤 batch 전체를 취소한다. 후보는 실제 은행 확인·자동 확정·돈 이동을 의미하지 않는다. CSV 가져오기 UI, AI, Toss SDK, 네이티브 빌드·스토어 제출은 이번 기능 범위 밖이다.

## 발견과 수정 확인

| 발견 | 최종 확인 |
| --- | --- |
| 하위 취소 POST의 busy가 상위 구간 전환 버튼에 전달되지 않아 전송 중 상세를 떠날 수 있음 | `mutationChange`가 상위 화면과 workspace를 함께 잠근다. 독립 지연 응답 fixture에서 취소 중 탭과 `연결 이력` 버튼이 비활성임을 확인했다. |
| 저장 결과 GET 중 새 후보 생성이 가능해 늦은 GET이 새 실행 결과를 덮을 수 있음 | `generate`가 `reading`을 검사하고 생성 버튼도 비활성이다. 저장 결과 GET을 지연한 독립 fixture로 확인했다. |
| 화면 전환으로 취소 key/사유가 사라질 위험 | 후보 화면은 tenant workspace 수명 동안 유지하고 취소 명령·사유는 상위 ID별 메모리에 둔다. 불확실한 취소 뒤 구간·탭 왕복 및 동일 key 재전송을 직접 확인했다. |
| 이력의 모든 행을 펼치면 한 페이지에서 많은 원본 조회가 발생함 | 목록은 metadata만 표시하고 상세는 처음 10행 및 명시적 더 보기다. 5개 batch 각각 100행인 fixture에서 목록의 행별 원본 조회가 없었다. 총괄 담당이 발견한 부하 경계이며 독립 검토자가 최종 동작을 확인했다. |

총괄 담당이 전달한 한국어 근거, 후보 버튼의 입금 식별 문구, 내부 규칙 버전 노출 제거, 원본 파일·계좌 별칭·거래번호·논리 레코드 표시도 최종 소스에서 확인했다.

## 업무·경계 검토

- 후보 생성의 기간은 실제 날짜·양끝 포함 최대 366일이다. 시작일은 입금 booked date에만 적용하며 종료일 이전의 오래된 미수 청구도 포함한다. 화면에 이 범위를 설명한다. 정상 0건도 1회, 기존 실행 GET·연결·취소는 0회다. 월·plan·한도·초기화 시각은 서버 값만 표시하고, 429와 월 변경은 별도 한국어 안내를 제공한다. 소진 뒤에도 저장 결과와 직접 연결이 열린다.
- 저장 후보는 생성 시각이 있는 불변 snapshot으로 표시한다. 원금·저장 당시 잔액·서버 제안액과 이름/날짜 근거·불일치 경고를 구분한다. 후보 0건·원본 조회 실패는 미입금이나 파일 미업로드의 증거로 표시하지 않는다. 원본 출처는 사용자 요청 후 조회하며 직접 등록과 CSV 출처를 서버 `sourceKind`로 구분한다.
- 금액은 정수 십진 문자열이다. 앱은 표시·입력 상한 검증만 하고 합계·배분·예상 잔액·quota 정책을 계산하지 않는다. 서버 제안액을 자동 확정하지 않으며 비후보 청구는 직접 선택할 수 있다. 1~100행과 유일한 입금/청구 쌍을 검증하고 여러 청구로 분할·여러 입금을 묶는 초안을 허용한다.
- 현재 잔액 검토는 각 행 양쪽의 서버 balance GET 결과를 보인다. 초안/사유 변경은 검토를 무효화하고 다음 명시적 확정만 기존 allocation POST를 호출한다. GET 묶음은 하나의 원자적 금융 snapshot이 아니며 화면도 확정 때 서버가 현재 잔액을 재검사함을 설명한다. 오래된 추천이나 부족한 현재 잔액은 확정 권한이 아니다.
- 확정/취소 POST의 저장된 명령 응답과 현재 batch GET을 분리한다. 확정 key 재생이 과거 version 1이어도 현재 GET은 취소된 version 2를 보여줄 수 있다. POST 성공 뒤 GET 실패를 금융 POST의 불명확 실패로 바꾸지 않는다. 이력에는 원래 행·금액·사유·시각과 확정/취소 감사 이벤트가 남는다. 취소는 필수 사유 및 전체 행 취소 안내 뒤 별도 POST이며 원본 삭제·환불로 표현하지 않는다.
- 정규화된 reason과 정렬된 lines, 객체 필드 순서가 같은 명령 key를 만든다. 불확실한 실패 뒤 초안을 유지하며 자동 재시도하지 않는다. 전송 중 입력·탭·내부 구간·뒤로가기를 잠근다. Android Back은 소스 확인이며 실제 기기 검증과 구분한다.
- 모든 금융 경로는 선택 tenant prefix를 사용한다. 기존 Session epoch가 이전 세션의 성공/401을 거부하고 `useLoad`가 오래된 조회 callback을 버린다. account/tenant workspace remount로 초안·key·결과를 분리한다. 샘플은 합성 읽기 전용 안내이며 후보/확정/취소 버튼이나 API 호출이 없다.

## 실제 실행 근거

독립 검토자는 Python Playwright와 `/usr/bin/chromium`으로 `http://127.0.0.1:8081`을 직접 실행했다. `/tmp/mobile-matching-review-fixture.py`와 `mobile-matching-review-fixture.log`의 11개 검사는 샘플 API 0회, 429 후 저장 결과 조회, 후보 경고, 불확실한 확정의 탭 왕복 초안·동일 key, POST 성공 뒤 현재 GET 실패 분리, 확정 응답 v1 뒤 현재 취소 v2, 320px 가로 넘침 없음, 저장 GET 중 생성 잠금, 큰 이력 목록의 조회 상한, 취소 중 탐색 잠금, 불확실한 취소의 사유·key 보존을 포함한다. 확정 2회와 취소 2회는 각각 같은 key였고 page error는 0개다. `/tmp/mobile-matching-review-{sample320,replay-cancelled320}.png`를 직접 열어 한국어 상태, 줄바꿈, 현재 취소 표시를 확인했다. 이 fixture는 UI 경계의 증거이며 실제 Java 금융 계산의 증거가 아니다.

총괄 담당의 [실제 서버 브라우저 스크립트](../scripts/verify-mobile-matching-browser.py), `/workspace/.local/run/matching-mobile-browser.log`도 직접 읽었다. 스크립트는 local HTTP와 `reconciliation_test`를 요구하고 합성 tenant를 정리하며 credential을 출력하지 않는다. 실제 9개 시나리오는 다음을 검증했다.

- 후보 성공 1회와 자동 배분 0건, 실제 commit 후 503으로 바꾼 확정 응답의 탭 왕복·동일 key 재시도 및 batch 1개.
- 총 미수 150,000원에서 부분 40,000원 확정 후 110,000원, 취소 후 150,000원.
- 40,000원+60,000원을 한 청구에 연결 후 50,000원, 취소 복원. 150,000원 입금을 두 청구 100,000원+50,000원에 분할 후 0원, 취소 후 150,000원.
- 확정·취소·조회 후 사용량 1회 유지, 취소된 batch 3개마다 감사 이벤트 2개, 실제 10회 소진·429 뒤 무료 저장 결과 조회, 320px 가로 넘침 없음.

해당 로그의 page error는 0개이며 합성 tenant 정리 완료가 기록돼 있다. [연결 후 홈](screenshots/mobile-matching-home-paid.png), [연결 이력](screenshots/mobile-matching-history.png), 후보 생성 화면을 직접 열어 보았다. 캡처 위치에 따라 긴 화면의 일부만 담기므로 이미지 하나로 전체 검증을 대체하지 않는다.

기존 core 실제 서버 브라우저 회귀도 `/workspace/.local/run/matching-mobile-core-regression.log`에서 9개 통과·page error 0개·합성 tenant 정리를 확인했다. 독립 실행한 `npm test`는 13개 모두 통과했다. 총괄 담당의 typecheck/unit 로그를 읽었고, backend XML 22개를 직접 합산해 186개·실패/오류/skip 0개를 확인했다. `matching-mobile-backend.log`는 `BUILD SUCCESSFUL`, API 로그는 8091 및 `reconciliation_test`를 기록한다. 독립 검토자가 Java/DB suite를 재실행한 것은 아니다.

## 남은 검증

브라우저의 mobile viewport는 iPhone/Android 실행을 대신하지 않는다. VoiceOver/TalkBack, OS 글자 확대, native 키보드·safe area·Android Back, SecureStore 실제 동작, Windows 개발 절차, 설치·서명·기기 빌드와 스토어 정책은 미검증이다. 서버 월 변경 오류 분기는 API/단위·기존 usage 검토 근거이며 이번 실제 브라우저에서 월을 넘겨 실행한 것은 아니다. 여러 tenant 간 화면 전환은 소스·기존 epoch 검사 근거이며 이번 실제 서버 브라우저는 단일 합성 tenant 흐름이다. 유료 모델 호출, 공개 배포·환경 publication, 스토어 제출은 수행하지 않았다.
