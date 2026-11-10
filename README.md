# 이리로 API 서버 (`iriro-server`)

Java 21 · Spring Boot 4.1.2 · Gradle Wrapper 9.8.2 · MariaDB 11.2. 모바일 앱과 AI 서버 사이의 API, 계정, 코스, 활동 기록을 담당합니다.

## 시작

1. 저장소 루트에서 `.env.example`을 `.env`로 복사하고 필요한 값을 입력합니다. `.env`는 Git에 올리지 않습니다.
2. Spring Boot는 저장소 루트의 `.env`를 실행할 때 자동으로 읽습니다.
3. IntelliJ에서 이 폴더를 Gradle 프로젝트로 열고 JDK 21을 선택합니다.
4. `./gradlew test`로 확인한 뒤 `./gradlew bootRun`으로 실행합니다.

`.env`에서 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AI_SERVER_URL`, `PORT`, `AUTH_JWT_SECRET`, `AUTH_ACCESS_TOKEN_TTL_SECONDS`, `AUTH_REFRESH_TOKEN_TTL_SECONDS`, `KAKAO_APP_ID`, `SPRING_PROFILES_ACTIVE`를 설정합니다. `AUTH_JWT_SECRET`은 32바이트 이상의 난수이며, 토큰 만료 시간은 각각 `1800`, `2592000`초입니다. `KAKAO_APP_ID`는 카카오 앱의 숫자 App ID입니다.
로컬에서는 `SPRING_PROFILES_ACTIVE=local`로 설정하면 Swagger UI를 `http://localhost:8080/swagger-ui.html`에서 볼 수 있습니다. Cloudtype의 기본 프로필에서는 Swagger가 꺼져 있습니다.
Cloudtype에는 같은 변수명을 서비스 환경변수로 등록합니다. Cloudtype에서 `SPRING_PROFILES_ACTIVE=local`을 설정하지 않습니다.
SQL 문장이 필요할 때만 로컬 `.env`의 `SQL_LOG_LEVEL=DEBUG`로 바꾸고 다시 실행합니다. 값은 로그에 표시하지 않습니다.
요청 로그에는 메서드·경로·상태·소요 시간만 남기며, 헤더·쿼리·본문은 기록하지 않습니다.

상태 확인: `GET /actuator/health`. API 기본 경로: `/api/v1`.

### 소셜 로그인

앱은 카카오·네이버에서 받은 Access Token을 `POST /api/v1/auth/login`의 `providerToken`으로 보냅니다. 서버는 제공자 사용자 정보를 확인하고 `(provider, providerUserId)`로 회원을 찾거나 생성한 뒤 이리로 Access Token과 Refresh Token을 발급합니다. 카카오는 토큰의 `app_id`가 `KAKAO_APP_ID`와 같은지도 확인합니다. 동일 이메일만으로 계정을 병합하지 않습니다.

카카오 인증 코드·토큰 발급 주소는 `kauth.kakao.com`이고, 현재 서버가 사용하는 토큰 정보·사용자 조회 주소는 `kapi.kakao.com`입니다. 네이버 사용자 조회 주소는 `openapi.naver.com`입니다. 현재 방식에는 카카오 REST API Key/Client Secret 또는 네이버 Client ID/Secret이 서버에 필요하지 않습니다. 실제 연동은 각 앱에서 받은 Access Token으로 로컬 Swagger의 로그인 API를 호출해 확인합니다. 실제 토큰을 로그·문서·Git에 남기지 않습니다.

### Cloudtype DB 연결

- DB 이름: `iriro`
- 로컬 실행용 `DB_URL`: `jdbc:mariadb://svc.sel3.cloudtype.app:31866/iriro`
- Cloudtype 내부 실행용 `DB_URL`: `jdbc:mariadb://iriro-db:3306/iriro`
- `DB_USERNAME`, `DB_PASSWORD`는 별도 공유받아 `.env`에 입력합니다. 비밀번호는 Git에 올리지 않습니다.
- `.env`를 읽기 위해 `source` 명령을 실행할 필요는 없습니다. IntelliJ에서 실행할 때는 작업 디렉터리가 저장소 루트인지 확인합니다. 배포를 다시 만들면 외부 주소·포트를 확인해 갱신합니다.

## 구조

- `src/main/java/com/iriro/server/`: `auth`, `member`, `trail`, `river`, `activity`, `favorite`, `notification`, `weather`, `badge`별 패키지.
- 각 기능은 아래 네 폴더를 사용합니다. 새 파일은 담당 역할에 맞는 폴더에 둡니다.

```text
auth/                     # 다른 기능도 같은 구조
├── presentation/         # Controller, 요청·응답 DTO, 입력 검증
├── application/          # 유스케이스, 서비스, 트랜잭션 조율
├── domain/               # Entity, 값 객체, 핵심 규칙
└── infrastructure/       # JPA 구현, 외부 API 어댑터
```

- `common/`: 기능에 속하지 않는 공통 응답·오류 처리만 둡니다.
- `src/main/resources/db/migration/`: Flyway SQL. 테이블은 마이그레이션으로만 변경합니다.
- `src/test/`: 기능별 테스트를 구현 코드와 같은 패키지에 둡니다.
- `docs/`: 개발·배포 계약과 구조 설명.

개발 순서: 명세의 실패 케이스 테스트 → 최소 구현 → 성공 케이스 테스트 → 정리. `.env`, 키, DB 비밀번호는 커밋하지 않습니다.
