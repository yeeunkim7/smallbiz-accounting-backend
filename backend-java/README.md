# 입출금 대사 백엔드 (Java)

이 디렉터리가 실행 단위입니다. 프로젝트 소개는 [../README.md](../README.md), 집계·검토 규칙은 [docs/design.md](docs/design.md)를 봅니다.

## 준비

- JDK 21. `JAVA_HOME`은 본인 PC의 JDK 21 설치 경로로 지정합니다.
- PostgreSQL. 이 앱 전용 데이터베이스를 **개발용**과 **테스트용**으로 나눕니다. Python 앱 SQLite나 다른 서비스 DB URL을 넣지 않습니다.
- 테스트 URL이 개발 DB(`reconciliation_dev`)이면 가드가 거부합니다. H2는 사용하지 않습니다.
- 시연 전용 DB를 따로 만들 필요는 없습니다.

| 용도 | 변수 | 예 |
| --- | --- | --- |
| 앱 기동 | `DB_URL` | `jdbc:postgresql://localhost:5432/reconciliation_dev` |
| 앱 기동 | `DB_USERNAME` | PostgreSQL 역할 이름 |
| 앱 기동 | `DB_PASSWORD` | 세션에서만 입력 |
| 테스트 | `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/reconciliation_test` |
| 테스트 | `TEST_DB_USERNAME` | PostgreSQL 역할 이름 |
| 테스트 | `TEST_DB_PASSWORD` | 세션에서만 입력 |

스키마는 기동·테스트 시 Flyway V1–V7가 적용합니다. JPA는 `validate`만 합니다. 자동 테스트에서 `reconciliation_test`에 V7이 적용된 것은 확인했습니다. 개발 DB 스키마 버전은 이 문서에서 단정하지 않습니다.

## 앱 기동

새 PowerShell에서 `backend-java`로 이동합니다. 현재 수동 확인 환경은 개발 DB `reconciliation_dev`와 포트 **8082**입니다. 8080이 비어 있으면 `--args`를 빼도 됩니다.

```powershell
Set-Location "C:\Users\김예은\Desktop\Install\smallbiz-accounting-backend\backend-java"

$env:JAVA_HOME = "본인_JDK21_설치경로"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:DB_URL = "jdbc:postgresql://localhost:5432/reconciliation_dev"
$env:DB_USERNAME = Read-Host "PostgreSQL role name"
$secure = Read-Host "PostgreSQL password" -AsSecureString
$bstr = $null
try {
	$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
	$env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringUni($bstr)
	.\gradlew.bat bootRun --args="--server.port=8082"
}
finally {
	if ($null -ne $bstr) {
		[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
	}
	Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
```

`GET /health`는 프로세스 기동만 확인하며 DB를 보장하지 않습니다.

## 화면

앱을 띄운 뒤 브라우저에서 엽니다. 현재 수동 확인은 `http://localhost:8082/`입니다.

- 거래처: 목록을 보고 등록하거나, 행을 눌러 이름·정산 방식만 수정합니다. 코드는 등록 후 바꿀 수 없습니다.
- CSV 업로드: 업무·은행을 구분해 파일 한 개를 `file` 필드로 보냅니다. 은행은 기존 6열 CSV 또는 우리은행 거래 표 7열 CSV입니다. 우리은행은 표만 추출한 UTF-8 CSV를 받으며, XLS/XLSX와 계좌 요약 행은 지원하지 않습니다. 안내 문구는 편의를 위한 것이고, 최종 결과는 서버 응답입니다. 실패하면 이번 요청은 저장되지 않습니다.
- 업로드 이력: 전체/BUSINESS/BANK 필터와 서버 페이지로 성공 이력을 봅니다. 목록은 업로드 시각·번호 내림차순입니다. 행을 누르면 단건 메타데이터를 봅니다. 원본 다운로드·삭제는 없습니다.
- 일별 조회: 시작일·종료일을 고르고 조회합니다. 날짜 행을 누르면 상세로 갑니다. 금액 비교와 검토 상태는 서로 다릅니다. 같은 화면에서 조회가 두 번 나가던 호출은 제거했습니다.
- 월별 조회: 연월을 고릅니다. 입금·출금·날짜 수 카드를 묶음으로 봅니다. 월 합계가 같아도 일별 차이가 남을 수 있습니다. 데이터가 없는 달은 안내 문구가 나옵니다.
- 날짜 상세: 집계, 업무·은행 원본(쪽 이동), 검토 상태·메모를 보고 미검토 또는 검토 완료만 저장합니다. 은행 원본 표는 거래일, 거래일시, 입출금 구분(입금/출금), 거래금액, 기재내용, 거래구분, 거래 후 잔액, 취급점입니다. 우리은행 생성 식별값과 6열 원천 거래 ID는 기본 표에 없고, 행을 누르면 상세에서 의미가 구분되어 보입니다. 재확인은 업로드 후에만 생기며 PUT 상태로 직접 보낼 수 없습니다. 업로드 뒤에는 집계·원본·검토를 다시 조회하세요.

사용 순서: 거래처 등록 → CSV 업로드 → 업로드 이력 확인 → 일별·월별 조회 → 날짜 상세에서 검토.

단일 계좌를 전제합니다. 우리은행 지문은 거래일시·입출금·거래금액·거래 후 잔액이 같으면 한 건으로 보고, 은행 고유 거래번호가 아닙니다. 기존 6열 CSV와 우리은행 양식 사이에서는 같은 실거래 중복을 식별하지 못합니다.

## 테스트

개발 DB와 **다른** 보호된 데이터베이스 `reconciliation_test`를 가리킵니다. 테스트를 개발 DB에서 돌리지 않습니다.

```powershell
Set-Location "C:\Users\김예은\Desktop\Install\smallbiz-accounting-backend\backend-java"

$env:JAVA_HOME = "본인_JDK21_설치경로"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/reconciliation_test"
$env:TEST_DB_USERNAME = Read-Host "PostgreSQL role name"
$secure = Read-Host "PostgreSQL password" -AsSecureString
$bstr = $null
try {
	$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
	$env:TEST_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringUni($bstr)
	.\gradlew.bat test
}
finally {
	if ($null -ne $bstr) {
		[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
	}
	Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
}
```

`TEST_DB_*`가 없으면 테스트는 실패하는 것이 정상입니다. HTML 보고서는 `build/reports/tests/test/index.html`입니다.

최근 확인: **2026-10-02 13:15:42** HTML 보고서 기준 실행 45 · 성공 45 · 실패 0 · 건너뜀 0. `BankUploadIntegrationTest` 9개에 우리은행 신규 3개가 포함되어 있습니다. 같은 실행에서 `reconciliation_test`에 V7이 적용되었습니다.

## 합성 CSV 예제

위치: [`docs/examples/`](docs/examples/). `backend-java`를 작업 디렉터리로 두고 아래 순서로 호출합니다. 아래 curl 호스트는 8082 기준입니다.

1. 선불·후불 거래처를 등록합니다 (`V-PRE-01`, `V-POST-01`). JSON은 현재 디렉터리의 파일로 보냅니다.
2. `docs/examples/business-valid.csv`를 업무 업로드합니다. 따옴표 안 비고는 `business-quoted-note.csv`입니다.
3. 은행은 둘 중 하나입니다.
   - 기존 6열: `docs/examples/bank-valid.csv` (`booked_date,direction,amount,counterparty_name,description,source_line_id`).
   - 우리은행 표: `docs/examples/bank-woori-valid.csv` (`거래일시,거래구분,기재내용,출금금액,입금금액,잔액,취급점`). 표만 추출한 UTF-8 CSV입니다. 거래처 등록은 필요 없습니다.
4. 일별·월별 집계와 원본·검토를 조회합니다.

헤더 오류: `business-invalid-header.csv`. 금액 오류: `business-invalid-amount.csv`, `bank-invalid-amount.csv` (400, 이번 요청 행은 남지 않음). 우리은행 금액은 천 단위 쉼표와 선택적 `원`을 허용하며, 기존 6열 금액 규칙은 바꾸지 않습니다.

## 대표 API

아래는 `http://localhost:8082` 기준입니다. 포트가 다르면 호스트만 바꿉니다. PowerShell에서 JSON `-d` 따옴표가 깨지면 `--data-binary "@파일.json"`을 씁니다.

```powershell
curl.exe -s http://localhost:8082/health
```

```powershell
@'
{"vendorCode":"V-PRE-01","vendorName":"가상 선불 거래처","settlementType":"PREPAID"}
'@ | Set-Content -Encoding utf8 vendor-prepaid.json
curl.exe -s -D - -X POST http://localhost:8082/api/v1/vendors -H "Content-Type: application/json" --data-binary "@vendor-prepaid.json"
```

```powershell
curl.exe -s "http://localhost:8082/api/v1/vendors?page=0&size=20"
curl.exe -s http://localhost:8082/api/v1/vendors/1
```

```powershell
curl.exe -s -D - -X POST http://localhost:8082/api/v1/uploads/business -F "file=@docs/examples/business-valid.csv;type=text/csv"
curl.exe -s -D - -X POST http://localhost:8082/api/v1/uploads/bank -F "file=@docs/examples/bank-valid.csv;type=text/csv"
curl.exe -s -D - -X POST http://localhost:8082/api/v1/uploads/bank -F "file=@docs/examples/bank-woori-valid.csv;type=text/csv"
```

```powershell
curl.exe -s "http://localhost:8082/api/v1/reconciliations/daily?from=2026-09-01&to=2026-09-30"
curl.exe -s "http://localhost:8082/api/v1/reconciliations/monthly?yearMonth=2026-09"
curl.exe -s "http://localhost:8082/api/v1/reconciliations/daily/2026-09-03/business"
curl.exe -s "http://localhost:8082/api/v1/reconciliations/daily/2026-09-03/bank?page=0&size=20"
curl.exe -s "http://localhost:8082/api/v1/uploads"
curl.exe -s "http://localhost:8082/api/v1/uploads/1"
curl.exe -s "http://localhost:8082/api/v1/reconciliations/daily/2026-09-03/review"
```

은행 원본 JSON에는 `bookedAt`, `balanceAfter`, `txnType`, `branchName`이 있습니다. 기존 6열은 null입니다. 우리은행 행의 `sourceLineId`는 지문이며 은행 거래번호로 해석하지 않습니다.

검토 저장은 조회한 `version`이 필요합니다. `NEEDS_RECHECK`는 PUT으로 넣지 않습니다.

```powershell
@'
{"status":"REVIEWED","memo":"확인","version":1}
'@ | Set-Content -Encoding utf8 review.json
curl.exe -s -X PUT http://localhost:8082/api/v1/reconciliations/daily/2026-09-03/review -H "Content-Type: application/json" --data-binary "@review.json"
```

## 오류와 한도

| HTTP | 의미 |
| --- | --- |
| 400 | 입력·CSV 검증 실패, 조회 파라미터 오류, 현금 데이터가 없는 날짜의 검토 저장 |
| 404 | 없는 거래처·업로드 (`VENDOR_NOT_FOUND`, `UPLOAD_NOT_FOUND`) |
| 409 | 거래처 코드 중복, 동일 파일 해시, 같은 테이블 `source_line_id` 중복, 검토 `version` 불일치 |
| 413 | 업로드 파일이 5MiB를 넘음 |

공통 본문: `code`, `message`, 필요 시 `fieldErrors`. DB 원문과 스택은 응답에 넣지 않습니다.

| 한도 | 값 |
| --- | --- |
| CSV 본문 | 5MiB (multipart 요청 여유는 설정 6–7MB) |
| 데이터 행 | 10,000 |
| 금액 | 1 ~ 1,000,000,000,000 (원, 정수만. 우리은행 표는 전용 금액 해석) |
| 일별 조회 구간 | 양끝 포함 최대 366일 |
| 목록 page size | 기본 20, 최대 100 |
| 검토 메모 | 최대 2,000자 |
| 오류 항목 | 응답 최대 100개, 초과 시 `truncated` |
