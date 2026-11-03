# 사업체별 은행 CSV 가져오기 — 기능 3b

인증된 멤버가 사업체의 은행 내역을 가져오면 입금 행은 미할당 `BANK_CSV` 입금으로 생성된다. 기존 입금·잔액·배분·후보 API에서 조회할 수 있으며 청구를 자동 선택하거나 배분하지 않는다. 출금만 있는 파일도 원본으로 기록하고 새 입금은 0건이다. 이를 실제 입금 누락으로 해석하지 않는다. V12는 이력·거래 식별·원본 근거·입금 출처를 추가하고, V1–V11 및 기존 전역 업로드·은행 자료는 변경하거나 사업체에 귀속하지 않는다.

## API

모든 경로는 `/api/v1/tenants/{tenantId}`로 시작하며 `Authorization: Bearer <session>`이 필요하다. 직접 호출을 포함한 각 서비스 진입점에서 활성 세션과 사업체 멤버십을 확인한다. 응답에는 `Cache-Control: no-store`를 적용한다.

| 요청 | 계약 |
| --- | --- |
| `POST /bank-imports` | `multipart/form-data`의 `file`, `bankAccountKey`와 `Idempotency-Key` 헤더 필수. 저장된 import를 반환하며 재전송도 201 |
| `GET /bank-imports?page=0&size=20` | `createdAt DESC,id DESC` 순 목록. `items,page,size,hasNext` 반환 |
| `GET /bank-imports/{id}` | 저장된 메타데이터와 건수 |
| `GET /bank-imports/{id}/rows?page=0&size=20` | 출금·겹치는 중복을 포함한 CSV 레코드 순 원본 |
| `GET /payments/{id}/source` | 가져온 입금의 원본. 수동 입금·없는 ID·다른 사업체 ID는 404 |

페이지는 0부터 시작하고 크기는 1~100이다. 행 offset은 검증한 입력과 long 산술을 사용한다. 사업체 접근 불가는 403, 접근 가능한 사업체 안의 다른 소유자 ID와 없는 ID는 동일한 404다.

import 필드는 `id,tenantId,bankAccountKey,fileSha256,filename,byteCount,rowCount,importedCount,duplicateCount,withdrawalCount,createdBy,createdAt`이다. `importedCount`는 새 입금 수, `duplicateCount`는 같은 계좌에서 이미 가져온 입금 행 수, `withdrawalCount`는 중복을 포함한 모든 출금 행 수다. 세 값의 합은 `rowCount`다. 동일 파일은 기존 건수와 메타데이터를 반환하며 새 업로드 이벤트를 만들지 않는다.

원본 필드는 `id,importId,recordNumber,outcome,originalCells,transactionId,bankAccountKey,sourceLineId,bookedDate,direction,amount,payerName,note,paymentId,firstSourceRowId`다. `outcome`은 `IMPORTED`, `DUPLICATE`, `WITHDRAWAL` 중 하나이며 `amount`는 십진 문자열이다. 출금의 `paymentId`는 null이다. `firstSourceRowId`는 안정적인 거래 ID를 최초로 관측한 원본을 가리키고, 이후 겹치는 파일의 관측 행도 각각 보존한다.

## 입력과 거래 식별

현재는 아래 6열 형식만 받으며 헤더 이름·순서가 정확히 일치해야 한다.

```csv
booked_date,direction,amount,counterparty_name,description,source_line_id
2026-11-01,IN,100000,Kim,invoice reference,bank-txn-000001
2026-11-01,IN,100000,Kim,second real payment,bank-txn-000002
2026-11-01,OUT,3000,Bank,fee,bank-txn-000003
```

[합성 예제](../backend-java/docs/examples/receivables-bank-valid.csv) · [로컬 HTTP 검증 절차](RECEIVABLES_IMPORT_SMOKE.md)

`bankAccountKey`는 사용자가 정한 비밀값이 아닌 계좌 별칭이다. 한 사업체 안에서 같은 원본 계좌는 같은 별칭, 다른 계좌는 다른 별칭을 유지해야 한다. 계좌나 소유권이 인증됐다는 뜻은 아니다. 별칭과 `source_line_id`는 대소문자를 구분하는 `[A-Za-z0-9._:-]{1,100}`이며 멱등성 키도 같은 형식을 사용한다.

`source_line_id`는 겹치는 기간의 내보내기에도 변하지 않는 은행/내보내기 제공자의 거래 ID여야 한다. 행 번호, 날짜·금액·이름 조합, 파일 안 순번이나 생성한 fingerprint는 대체 근거가 아니다. 서버는 제공자가 주장한 ID의 신뢰성을 확인하지 못한다. 날짜·금액·입금자·설명이 같아도 서로 다른 실제 거래라면 별도 ID를 유지하며 두 입금 모두 생성한다. 다른 파일이 같은 ID에 다른 정규화 날짜·방향·금액·입금자·메모를 제공하면 409로 전체 import를 롤백한다. 출금 ID도 검사한다. 기존 우리은행 7열은 은행 고유 ID 대신 fingerprint를 생성하므로 여기서는 지원하지 않고 `W1.<64자리 소문자 hex>`도 거부한다. 기존 업로드는 legacy 개발 경계에서 기존 계약을 유지한다.

UTF-8을 엄격하게 해독하고 선두 BOM은 허용한다. 기존 `BankCsvParser`의 6열 계약과 Apache Commons CSV RFC4180 라이브러리를 재사용하고, 금액·날짜·문자 검증은 `ReceivablesValidation`을 사용한다. legacy 파서의 느슨한 UTF-8 해독·연도·UTF-16 문자 규칙을 그대로 적용하지 않도록 파서 자체는 호출하거나 변경하지 않는다. 텍스트 안 LF·탭은 허용하지만 CR 및 기타 제어 문자는 거부한다. 레코드 구분은 CRLF·LF 모두 허용한다. 빈 파일·헤더만 있는 파일·빈 레코드·파일 안 ID 중복·불일치 열·추가 헤더·잘못된 인코딩/CSV는 전체 거부한다.

금액은 `1`~`1000000000000`의 정확한 양수 KRW 정수 문자열이다. 공백·천 단위 쉼표·소수·부호·지수·선행 0은 허용하지 않는다. 날짜는 연도 0001~9999의 실제 `YYYY-MM-DD`, 방향은 정확한 `IN`/`OUT`이다. 입금자·메모는 수동 등록 검증기로 앞뒤 공백을 제거하며 각각 100/1,000 Unicode 코드 포인트까지 허용한다. ID·날짜·방향·금액에는 앞의 엄격한 형식을 적용한다. `originalCells`는 공백·따옴표 안 LF를 포함한 파싱 원문 문자열을 정규화 값과 별도로 보존한다. 원본 근거는 파일 바이트 상한으로 제한하고 임의로 잘라 저장하지 않는다.

## 상한과 원자성

기본 상한은 파일당 **1MiB·데이터 1,000행**이다. `app.receivables.bank-import.max-bytes`와 `.max-rows`로 설정하며 각각 1~5,242,880바이트, 1~10,000행 밖의 값은 시작 시 거부한다. multipart 메타데이터와 무관하게 실제 stream에서 상한+1바이트까지만 읽는다. 기존 업로드 계약을 위해 전송 계층의 part 6MB/request 7MB 한도는 유지하고 tenant 서비스가 더 작은 상한을 적용한다. 파일명은 basename만 사용하고 기본 `bank.csv`, 제어 문자 및 255 UTF-16 단위 초과는 거부한다. 파일명은 표시 정보로 요청 의미에서 제외해 안전한 이름 변경 후에도 재전송할 수 있다.

원본 바이트의 SHA-256을 저장하지만 원본 파일 자체를 보관하거나 재다운로드하지 않는다. `originalCells`는 순서가 있는 6개 셀 값이며 CSV 인용 문법·구분자 표기·BOM·레코드 줄바꿈의 바이트 보존은 아니다. `recordNumber`는 헤더 1을 포함한 논리 레코드 번호로, 셀 안 개행이 있으면 물리 줄 번호와 다르다. 헤더는 이 버전의 고정 계약이다. 원본 셀과 텍스트는 비신뢰 자료로 렌더링하며 향후 스프레드시트 내보내기를 추가하면 별도 이스케이프·수식 무력화가 필요하다. 현재 importer는 원본 내용을 오류나 로그에 넣지 않는다.

검증을 마친 뒤 bounded 동기 트랜잭션으로 사업체 행을 잠가 import를 직렬 처리한다. 세션·멤버십을 재확인하고 import·원본 관측·안정적 거래 ID·입금·멱등성 기록을 함께 커밋한다. 사업체 복합 FK로 각 자료를 연결하며 `MANUAL` 입금은 은행 출처 null, `BANK_CSV`는 출처 필수다. 배분 확정·취소는 원본을 덮어쓰지 않는다. 뒤늦은 거래 ID 충돌·저장 오류도 전체 import와 키를 롤백해 수정 후 재시도할 수 있다. 사업체별 직렬화는 MVP 처리량의 제한이며 별도 백그라운드 worker는 없다.

중복 기준은 다음과 같다.

- 같은 `(tenant,Idempotency-Key)`와 같은 `(bankAccountKey,원본 파일 SHA-256)`는 최초 결과를 반환한다. 기존 키에 다른 내용이면 409 `IDEMPOTENCY_CONFLICT`다.
- 새 키로 같은 `(tenant,bankAccountKey,원본 파일 SHA-256)`을 요청하면 기존 import를 재생하고 새 키도 같은 트랜잭션에 기록한다. 바이트가 같아도 계좌 별칭이 다르면 별도 import다.
- 다른 파일의 중복은 `(tenant,bankAccountKey,source_line_id)`와 정규화 내용의 일치로 판단하며 파일별 관측은 보존한다. 정규화 후 같은 공백 차이는 허용한다. 바이트·BOM·줄바꿈 변경은 파일 해시를 바꾸므로 새 요청 키가 필요하지만 안정적 거래 ID로 입금 중복은 방지한다.

오류는 안전한 `code,message`와 가능한 경우 `recordNumber,field`를 반환한다. 인코딩·형식·입력은 400 `BANK_IMPORT_INVALID`, 바이트·행 상한은 413 `BANK_IMPORT_LIMIT`, 기존 거래 ID 내용 변경은 409 `BANK_TRANSACTION_CONFLICT`, 요청 내용 변경은 409 `IDEMPOTENCY_CONFLICT`, 조회 불가는 404 `BANK_IMPORT_NOT_FOUND`, 저장 오류는 SQL 없이 503 `BANK_IMPORT_STORAGE_UNAVAILABLE`이다. 페이지 검증은 기존 400 `INVALID_INPUT`, 인증·multipart 오류는 기존 서버 경계를 따른다. 부분 저장이나 실패한 시도를 성공 import로 기록하지 않는다.

## 현재 범위와 검증 근거

import는 AI 호출·월 사용량 차감·배분·은행 소유권 검증을 수행하지 않는다. 후보 생성의 별도 기본 상한인 유효 입금 200건/청구 200건은 그대로이므로 1,000행 import 성공이 전체 범위 후보 실행 성공을 뜻하지 않는다. 범위를 조정해야 하며 상한 초과 후보 실행은 조용히 일부만 처리하지 않고 실패한다. 오래된 미수 청구도 후보 대상이므로 입금 날짜를 줄이는 것만으로 해결되지 않을 수 있다. [성공 실행 한도](RECEIVABLES_USAGE.md)는 새 후보 실행에만 적용하며 한도 소진이 import를 막지 않는다.

Expo 핵심 화면은 구현됐지만 모바일 CSV 가져오기 화면은 후속이다. 보존·삭제·저장량 정책과 공개 운영 제어도 남아 있다. 실제 실행 결과와 미검증 범위는 [import 독립 검토](receivables-import-review.md)와 [HTTP 검증](RECEIVABLES_IMPORT_SMOKE.md)에 보존한다. 이 문서 수정 과정에서는 테스트·DB 변경을 실행하지 않았다.
