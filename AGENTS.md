# 이리로 백엔드 개발 규칙

- Java 21, Spring Boot 4.1.2, Gradle Wrapper, MariaDB 11.4를 사용한다.
- 기능별 패키지(`auth`, `member`, `trail`, `river`, `activity`, `favorite`, `notification`, `weather`, `badge`)에 코드를 둔다. 각 기능의 `presentation`은 HTTP·DTO·입력 검증, `application`은 유스케이스·트랜잭션, `domain`은 핵심 규칙, `infrastructure`는 DB·외부 API 구현을 맡는다.
- API 구현 전 해당 명세의 실패·성공 테스트를 작성하고 `./gradlew test`로 확인한다.
- DB 변경은 `src/main/resources/db/migration`의 새 Flyway 파일로 작성한다. 기존 적용 마이그레이션은 수정하지 않는다.
- 실제 키·토큰·비밀번호·`.env`를 Git에 올리지 않는다.
- API 응답과 오류 코드는 Notion API 명세를 따른다. 불명확한 계약은 임의로 결정하지 않고 팀에 확인한다.
