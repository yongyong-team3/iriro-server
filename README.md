# 이리로 API 서버 (`iriro-server`)

Java 21 · Spring Boot 4.1.2 · Gradle Wrapper 9.8.2 · MariaDB 11.2. 모바일 앱과 AI 서버 사이의 API, 계정, 코스, 활동 기록을 담당합니다.

## 시작

1. 저장소 루트에서 `.env.example`을 `.env`로 복사하고, 전달받은 DB 사용자명과 비밀번호를 입력합니다. `.env`는 Git에 올리지 않습니다.
2. Spring Boot는 저장소 루트의 `.env`를 실행할 때 자동으로 읽습니다.
3. IntelliJ에서 이 폴더를 Gradle 프로젝트로 열고 JDK 21을 선택합니다.
4. `./gradlew test`로 확인한 뒤 `./gradlew bootRun`으로 실행합니다.

상태 확인: `GET /actuator/health`. API 기본 경로: `/api/v1`.

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
