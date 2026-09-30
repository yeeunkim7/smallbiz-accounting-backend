# 입출금·정산 대사 백엔드 (Java)

업무 데이터의 입출금 예정과 은행 거래 실제액을 비교하는 MVP용 Spring Boot 애플리케이션이다. 이 디렉터리가 실행 단위다.

저장소 루트의 Python FastAPI 앱(`app/`)은 기존 전표 실험 코드다. **삭제하거나 대체하지 않는다.** 두 앱은 DB도 프로세도 분리한다. Python 쪽 SQLite/기존 PostgreSQL 스키마를 이 앱이 변경하지 않는다.

설계 상세는 [docs/design.md](docs/design.md)를 본다.

## 버전

| 구성 | 버전 | 근거 |
| --- | --- | --- |
| Java | 21 | 요구사항. Spring Boot 4.1은 Java 17–26 |
| Spring Boot | 4.1.1 | [Spring Boot 4.1 시스템 요구사항](https://docs.spring.io/spring-boot/4.1/system-requirements.html) GA |
| Gradle Wrapper | 9.7.1 | Boot 4.1이 Gradle 8.14+ 및 9.x 지원 |
| DB | PostgreSQL 16 이상 권장 | Flyway + `ddl-auto: validate` |

preview/snapshot은 사용하지 않는다.

## 현재 구현

- `GET /health` — 프로세스 기동만. DB를 보장하지 않는다.
- 거래처 등록·목록·단건·수정 (`/api/v1/vendors`)
- 업무 CSV 업로드 (`POST /api/v1/uploads/business`)
- 은행 CSV 업로드 (`POST /api/v1/uploads/bank`)
- Flyway V1 `vendor`, V2 `upload_file` / `business_event`, V3 SHA `VARCHAR`, V4 `BANK` 타입과 `bank_transaction`

## 후속 예정 (미구현)

- 일별·월별 입출금 집계와 차이 조회
- 날짜별 원본 조회, 검토 상태·메모

원본 CSV 파일 자체는 저장하지 않는다. 재다운로드 API는 없다.

## 실행 환경

- JDK 21
- PostgreSQL (이 앱 전용 데이터베이스 **2개**: 개발용, 테스트용)
- 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

비밀번호를 저장소나 이 문서에 적지 않는다.

개발 DB 예 (이름은 로컬에서 정한다):

```text
DB_URL=jdbc:postgresql://localhost:5432/reconciliation_dev
DB_USERNAME=자신의사용자
DB_PASSWORD=자신의비밀번호
```

테스트는 **개발 DB와 다른** 데이터베이스를 가리킨다.

```text
TEST_DB_URL=jdbc:postgresql://localhost:5432/reconciliation_test
TEST_DB_USERNAME=자신의사용자
TEST_DB_PASSWORD=자신의비밀번호
```

기존 Python 앱이 쓰는 DB URL을 넣지 않는다. 테이블을 공유하거나 Flyway로 기존 스키마를 덮어쓰지 않는다.

Docker로 PostgreSQL만 띄우는 예 (비밀번호는 환경변수로 넘긴다):

```text
docker run --name reconciliation-postgres -e POSTGRES_USER=recon -e POSTGRES_PASSWORD=%POSTGRES_PASSWORD% -e POSTGRES_DB=reconciliation_dev -p 5432:5432 -d postgres:16
```

테스트 DB는 같은 인스턴스에 `reconciliation_test`를 추가로 만들면 된다. 이 저장소는 Docker/시스템 서비스를 설치하거나 설정을 바꾸지 않는다.

## 빌드·실행·테스트

Windows PowerShell, `backend-java` 디렉터리에서:

```powershell
.\gradlew.bat build -x test
$env:DB_URL="jdbc:postgresql://localhost:5432/reconciliation_dev"
$env:DB_USERNAME="..."
$env:DB_PASSWORD="..."
.\gradlew.bat bootRun
```

테스트 (`reconciliation_test` 전용. `reconciliation_dev`와 같은 URL이면 가드가 거부한다):

```powershell
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/reconciliation_test"
# 첫 입력은 PostgreSQL 역할 이름이다. 비밀번호·숫자를 넣지 않는다.
$env:TEST_DB_USERNAME = Read-Host "PostgreSQL role name"
$secure = Read-Host "PostgreSQL password" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$env:TEST_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringUni($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
.\gradlew.bat test --no-daemon --rerun-tasks
Remove-Item Env:TEST_DB_PASSWORD
```

비밀번호는 위처럼 현재 세션에서만 넣고, 명령에 평문을 적지 않는다. H2는 사용하지 않는다. `TEST_DB_*`가 없으면 테스트는 실패하는 것이 정상이다.

## API 예시 (가상 거래처)

서버: `http://localhost:8080`

헬스 (DB와 무관):

```powershell
curl.exe -s http://localhost:8080/health
```

등록 (201):

```powershell
curl.exe -s -D - -X POST http://localhost:8080/api/v1/vendors `
  -H "Content-Type: application/json" `
  -d "{\"vendorCode\":\"V-PRE-01\",\"vendorName\":\"가상 선불 거래처\",\"settlementType\":\"PREPAID\"}"
```

목록 (200, `vendorCode` 오름차순, 0부터 시작하는 page):

```powershell
curl.exe -s "http://localhost:8080/api/v1/vendors?page=0&size=20"
```

단건·수정 (200). 거래처 코드는 수정 요청에 포함하지 않는다.

```powershell
curl.exe -s http://localhost:8080/api/v1/vendors/1
curl.exe -s -X PUT http://localhost:8080/api/v1/vendors/1 `
  -H "Content-Type: application/json" `
  -d "{\"vendorName\":\"가상 후불 거래처\",\"settlementType\":\"POSTPAID\"}"
```

입력 오류는 400, 없는 ID는 404, 중복 `vendorCode`는 409다. 오류 JSON 예:

```json
{
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "fieldErrors": [{ "field": "vendorName", "message": "거래처명은 필수입니다." }]
}
```

PowerShell에서 JSON을 직접 `-d`로 넣으면 따옴표가 깨질 수 있다. 거래처는 UTF-8 파일로 보내는 것이 안전하다.

## 업무 CSV 업로드

CSV 파서: Apache Commons CSV 1.14.0 (Java 8+, Maven Central; Boot 4.1.1 / Java 21과 함께 컴파일됨). `split(",")`는 사용하지 않는다. UTF-8 BOM은 파싱 전에 선두 3바이트만 제거한다. 파일 내용 SHA-256은 BOM을 포함한 원본 바이트 기준이다.

**1. 가상 거래처 등록** (이미 있으면 생략)

`C:\pg-temp\v-pre.json`:

```json
{"vendorCode":"V-PRE-01","vendorName":"가상 선불 거래처","settlementType":"PREPAID"}
```

`C:\pg-temp\v-post.json`:

```json
{"vendorCode":"V-POST-01","vendorName":"가상 후불 거래처","settlementType":"POSTPAID"}
```

```powershell
curl.exe -s -D - -X POST http://localhost:8080/api/v1/vendors -H "Content-Type: application/json" --data-binary "@C:\pg-temp\v-pre.json"
curl.exe -s -D - -X POST http://localhost:8080/api/v1/vendors -H "Content-Type: application/json" --data-binary "@C:\pg-temp\v-post.json"
```

**2. 정상 CSV 예** (`docs/examples/business-valid.csv`)

```text
vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
V-PRE-01,CHARGE_EXPECTED,,2026-09-03,500000,9월 선불 충전 요청,BIZ-20260903-001
V-PRE-01,USAGE,2026-09-03,,12000,사용 — 대사 제외,BIZ-20260903-002
V-POST-01,SETTLEMENT_EXPECTED,2026-08-15,2026-09-10,800000,8월분 후불 정산,BIZ-20260910-001
V-POST-01,REFUND_EXPECTED,2026-09-05,2026-09-12,30000,현금 환불 예정,BIZ-20260912-001
```

`backend-java` 디렉터리에서:

```powershell
curl.exe -s -D - -X POST http://localhost:8080/api/v1/uploads/business -F "file=@docs/examples/business-valid.csv;type=text/csv"
```

따옴표 안 개행·쉼표 비고 예: `docs/examples/business-quoted-note.csv` (거래처 `V-POST-01` 필요).

성공 시 201, `uploadId`, `originalFilename`, `rowCount`, `uploadedAt`.

**3. 오류 CSV 예** (금액이 정수가 아님 → 400, DB에 이번 요청 행이 남지 않음)

```text
vendor_code,event_type,usage_date,expected_cash_date,amount,note,source_line_id
V-PRE-01,CHARGE_EXPECTED,,2026-09-03,500000,정상,BIZ-MIX-001
V-PRE-01,USAGE,2026-09-03,,12.5,오류,BIZ-MIX-002
```

헤더 오류 예: `docs/examples/business-invalid-header.csv`.

파일 한도 5MiB, 데이터 10,000행. 동일 파일(내용 SHA-256) 또는 `source_line_id` 중복은 409. 검증 실패 시 이번 요청에서 추가한 `upload_file`/`business_event`는 남지 않는다. 원본 CSV 바이트는 보관하지 않는다.

## 은행 CSV 업로드

거래처 사전 등록은 필요 없다. 입금자명으로 거래처를 연결하지 않는다.

**정상 예** (`docs/examples/bank-valid.csv`)

```text
booked_date,direction,amount,counterparty_name,description,source_line_id
2026-09-03,IN,500000,가상입금,9월 입금,BANK-20260903-001
2026-09-12,OUT,30000,,현금 환불 출금,BANK-20260912-001
```

PowerShell에서 JSON·CSV는 파일을 통째로 보낸다. 최신 앱을 8081에서 띄운 경우 포트만 바꾼다.

```powershell
curl.exe -s -D - -X POST http://localhost:8081/api/v1/uploads/bank -F "file=@docs/examples/bank-valid.csv;type=text/csv"
```

성공 시 201, 같은 파일 재업로드는 409. 금액 오류 예: `docs/examples/bank-invalid-amount.csv` → 400.

업무와 은행에 같은 `source_line_id` 문자열이 있어도 테이블이 다르면 각각 저장된다. 파일 내용 SHA-256은 `upload_file` 전역 UNIQUE다.

## 패키지

기능별로 `health`, `vendor`, `upload`, `common`을 둔다. 업로드는 Controller → Facade(파싱, 트랜잭션 밖) → Service(`@Transactional` 저장) → Repository → DB 순이다. 파일 크기·SHA-256은 `UploadFileSupport`를 업무·은행이 같이 쓰고, 행 검증은 각 CSV 파서가 담당한다.
