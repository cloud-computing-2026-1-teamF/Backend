# Backend

## 실행 방법 (Korean Guide)

이 프로젝트는 **Docker PostgreSQL** 환경을 먼저 실행한 뒤, Spring Boot 애플리케이션을 구동하는 방식입니다.

### 1. 사전 준비

- Docker Desktop (또는 Docker Engine) 실행
- JDK 25 설치
- (선택) `./gradlew` 실행 권한 확인

### 2. PostgreSQL 컨테이너 실행

```bash
docker compose up -d postgres
```

기본 DB 설정:
- DB: `sanggwon_ai`
- USER: `sanggwon`
- PASSWORD: `sanggwon`
- HOST PORT: `5433`
- CONTAINER PORT: `5432`

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 실행 주소:
- 로컬 `./gradlew bootRun`: `http://localhost:8080`
- Docker Compose API: `http://localhost:8081`

다른 프로젝트와 포트가 겹치면 다음처럼 변경할 수 있습니다.

```bash
SANGGWON_POSTGRES_PORT=55433 SANGGWON_API_PORT=18081 docker compose up -d
```

### 4. 테스트 실행

테스트도 PostgreSQL을 사용하므로, DB 컨테이너가 켜져 있어야 합니다.

```bash
./gradlew test
```

### 5. 전체 Docker 실행 (API + DB)

```bash
docker compose up -d
```

### 6. 종료

```bash
docker compose down
```
