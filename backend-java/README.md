# 입출금 대사 백엔드 (Java)

이 디렉터리가 실행 단위입니다. 프로젝트 소개는 [../README.md](../README.md), 집계·검토 규칙은 [docs/design.md](docs/design.md)를 봅니다.

## 준비

- JDK 21. `JAVA_HOME`은 본인 PC의 JDK 설치 경로로 지정합니다.
- PostgreSQL. 이 앱 전용 데이터베이스를 **개발용**과 **테스트용**으로 나눕니다. Python 앱 SQLite나 다른 서비스 DB URL을 넣지 않습니다.
- 테스트 URL이 개발 DB(`reconciliation_dev`)이면 가드가 거부합니다. H2는 사용하지 않습니다.

| 용도 | 변수 | 예 |
| --- | --- | --- |
| 앱 기동 | `DB_URL` | `jdbc:postgresql://localhost:5432/reconciliation_dev` |
| 앱 기동 | `DB_USERNAME` | PostgreSQL 역할 이름 |
| 앱 기동 | `DB_PASSWORD` | 세션에서만 입력 |
| 테스트 | `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/reconciliation_test` |
| 테스트 | `TEST_DB_USERNAME` | PostgreSQL 역할 이름 |
| 테스트 | `TEST_DB_PASSWORD` | 세션에서만 입력 |

스키마는 기동·테스트 시 Flyway V1–V6가 적용합니다. JPA는 `validate`만 합니다.

## 앱 기동

`backend-java`에서. 기본 포트는 8080입니다. 이미 쓰이면 `--args=--server.port=8081`을 붙입니다.

```powershell
$env:JAVA_HOME = "본인_JDK21_설치경로"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:DB_URL = "jdbc:postgresql://localhost:5432/reconciliation_dev"
$env:DB_USERNAME = Read-Host "PostgreSQL role name"
$secure = Read-Host "PostgreSQL password" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringUni($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
.\gradlew.bat bootRun
Remove-Item Env:DB_PASSWORD
```

`GET /health`는 프로세스 기동만 확인하며 DB를 보장하지 않습니다.

## 화면

앱을 띄운 뒤 브라우저에서 엽니다.

- `http://localhost:8080/` (8081로 기동했으면 `http://localhost:8081/`)
- 일별 조회: 시작일·종료일을 고르고 조회합니다. 날짜 행을 누르면 상세로 갑니다.
- 월별 조회: 연월을 고릅니다. 입금·출금·날짜 수 카드를 묶음으로 봅니다. 월 합계가 같아도 일별 차이가 남을 수 있습니다. 데이터가 없는 달은 안내 문구가 나옵니다.
- 날짜 상세: 집계, 업무·은행 원본(쪽 이동), 검토 상태·메모를 보고 미검토 또는 검토 완료만 저장합니다. 재확인은 업로드 후에만 생기며 PUT 상태로 직접 보낼 수 없습니다. 현재가 재확인이면 최신 version으로 조회한 뒤 검토 완료로 저장할 수 있습니다.

거래처 등록과 CSV 업로드는 API로 한 뒤 이 화면에서 조회합니다. 화면에서 업로드하지 않습니다.

## 테스트

개발 DB와 **다른** 데이터베이스를 가리킵니다.

```powershell
$env:JAVA_HOME = "본인_JDK21_설치경로"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/reconciliation_test"
$env:TEST_DB_USERNAME = Read-Host "PostgreSQL role name"
$secure = Read-Host "PostgreSQL password" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$env:TEST_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringUni($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
.\gradlew.bat test
Remove-Item Env:TEST_DB_PASSWORD
```

`TEST_DB_*`가 없으면 테스트는 실패하는 것이 정상입니다. HTML 보고서는 `build/reports/tests/test/index.html`입니다.

## 합성 CSV 예제

위치: [`docs/examples/`](docs/examples/). `backend-java`를 작업 디렉터리로 두고 아래 순서로 호출합니다.

1. 선불·후불 거래처를 등록합니다 (`V-PRE-01`, `V-POST-01`). JSON은 현재 디렉터리의 파일로 보냅니다.
2. `docs/examples/business-valid.csv`를 업무 업로드합니다. 따옴표 안 비고는 `business-quoted-note.csv`입니다.
3. `docs/examples/bank-valid.csv`를 은행 업로드합니다. 은행은 거래처 등록이 필요 없습니다.
4. 일별·월별 집계와 원본·검토를 조회합니다.

헤더 오류: `business-invalid-header.csv`. 금액 오류: `business-invalid-amount.csv`, `bank-invalid-amount.csv` (400, 이번 요청 행은 남지 않음).

## 대표 API

아래는 `http://localhost:8080` 기준입니다. 8081이면 호스트만 바꿉니다. PowerShell에서 JSON `-d` 따옴표가 깨지면 `--data-binary "@파일.json"`을 씁니다.

```powershell
curl.exe -s http://localhost:8080/health
```

```powershell
@'
{"vendorCode":"V-PRE-01","vendorName":"가상 선불 거래처","settlementType":"PREPAID"}
'@ | Set-Content -Encoding utf8 vendor-prepaid.json
curl.exe -s -D - -X POST http://localhost:8080/api/v1/vendors -H "Content-Type: application/json" --data-binary "@vendor-prepaid.json"
```

```powershell
curl.exe -s "http://localhost:8080/api/v1/vendors?page=0&size=20"
curl.exe -s http://localhost:8080/api/v1/vendors/1
```

```powershell
curl.exe -s -D - -X POST http://localhost:8080/api/v1/uploads/business -F "file=@docs/examples/business-valid.csv;type=text/csv"
curl.exe -s -D - -X POST http://localhost:8080/api/v1/uploads/bank -F "file=@docs/examples/bank-valid.csv;type=text/csv"
```

```powershell
curl.exe -s "http://localhost:8080/api/v1/reconciliations/daily?from=2026-09-01&to=2026-09-30"
curl.exe -s "http://localhost:8080/api/v1/reconciliations/monthly?yearMonth=2026-09"
curl.exe -s "http://localhost:8080/api/v1/reconciliations/daily/2026-09-03/business"
curl.exe -s "http://localhost:8080/api/v1/reconciliations/daily/2026-09-03/bank?page=0&size=20"
curl.exe -s "http://localhost:8080/api/v1/uploads"
curl.exe -s "http://localhost:8080/api/v1/uploads/1"
curl.exe -s "http://localhost:8080/api/v1/reconciliations/daily/2026-09-03/review"
```

검토 저장은 조회한 `version`이 필요합니다. `NEEDS_RECHECK`는 PUT으로 넣지 않습니다.

```powershell
@'
{"status":"REVIEWED","memo":"확인","version":1}
'@ | Set-Content -Encoding utf8 review.json
curl.exe -s -X PUT http://localhost:8080/api/v1/reconciliations/daily/2026-09-03/review -H "Content-Type: application/json" --data-binary "@review.json"
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
| 금액 | 1 ~ 1,000,000,000,000 (원, 정수만) |
| 일별 조회 구간 | 양끝 포함 최대 366일 |
| 목록 page size | 기본 20, 최대 100 |
| 검토 메모 | 최대 2,000자 |
| 오류 항목 | 응답 최대 100개, 초과 시 `truncated` |
