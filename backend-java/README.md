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

- `GET /health` — 애플리케이션이 떠 있으면 `{"status":"UP"}`. **DB 상태를 검사하지 않는다.**
- 거래처 CRUD 중 등록·목록·단건·수정 (`/api/v1/vendors`)
- Flyway로 `vendor` 테이블 생성 (Java 전용 DB)

## 후속 예정 (미구현)

- 업무 CSV / 은행 CSV 업로드
- 일별·월별 입출금 집계와 차이 조회
- 날짜별 원본 조회, 검토 상태·메모

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

테스트:

```powershell
$env:TEST_DB_URL="jdbc:postgresql://localhost:5432/reconciliation_test"
$env:TEST_DB_USERNAME="..."
$env:TEST_DB_PASSWORD="..."
.\gradlew.bat test
```

H2는 사용하지 않는다. `TEST_DB_*`가 없으면 테스트는 실패하는 것이 정상이다.

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

## 패키지

기능별로 `health`, `vendor`, `common`만 둔다. 거래처 요청은 Controller → Service → Repository → DB 순이다. Entity를 API 본문으로 쓰지 않는다.
