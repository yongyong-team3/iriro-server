# Cloudtype 이리로 백엔드 배포값

기준: 2026-11-12, `@soeunverse/newsback:main`의 `iriro-db`(MariaDB 11.2)와 `iriro-ai`(Python, 8000)가 실행 중인 상태. 백엔드 저장소는 `https://github.com/yongyong-team3/iriro-server.git`이며 Java 21 / Spring Boot 4.1.2 / Gradle Wrapper를 사용한다.

## 현재 배포 가능 상태

Cloudtype 화면의 Free Tier 할당은 **메모리 1GB/1GB, 디스크 1GB/1GB, 실행 서비스 2/4**다. 실행 중인 DB와 AI가 한도를 사용한다. 서비스 개수는 2개 남아 있지만, 메모리가 남지 않아 백엔드를 같은 스페이스에 동시에 실행하려면 **추가 리소스**가 필요하다. 기존 두 서비스를 중지하거나 재설정하여 한도를 비우는 경우 서비스가 끊길 수 있다. 배포 설정을 제출하기 전에 백엔드용 메모리(권장 시작값 512MB 이상)와 필요한 디스크 할당을 Cloudtype 화면에서 확인한다.

현재 Free Tier의 DB 디스크는 임시다. Cloudtype [요금 안내](https://cloudtype.io/pricing)에 따르면 임시 디스크는 초기화될 수 있고, 무료 서비스는 매일 한 차례 중지된다. 실제 사용자 데이터를 보존해야 한다면 DB에 영구 디스크 또는 별도 지속형 DB를 사용한다.

## 서비스 생성값

| 항목 | 값 |
| --- | --- |
| 프로젝트/환경 | 기존 `newsback` / `main` (AI·DB와 같은 환경) |
| 서비스명 | `iriro-server` |
| 소스 | `https://github.com/yongyong-team3/iriro-server.git`, 배포할 최신 브랜치/커밋 선택 |
| 빌드 방식 | 저장소 루트 `Dockerfile` |
| 런타임 | Java 21 (`eclipse-temurin:21-jre`) |
| 공개 포트 | `8080` / HTTP |
| 앱 포트 | `PORT=8080` |
| 시작 명령 | Dockerfile의 `ENTRYPOINT`; Cloudtype에서 별도 명령 덮어쓰지 않음 |
| 준비 확인 | `GET /actuator/health`가 HTTP 200, `status: UP` |

Cloudtype가 GitHub 조직 저장소를 목록에 보여 주지 않으면 **Git URL로 배포**를 선택하고 위 소스 URL과 브랜치를 지정한다. 비공개 저장소라면 Cloudtype GitHub 연동 권한 또는 deploy key가 필요하다. Dockerfile은 빌드 단계에서 `bootJar`를 실행하며 실행 이미지에는 JRE와 jar만 넣는다. `.dockerignore`는 로컬 `.env`와 Git 파일이 빌드 컨텍스트에 들어가는 것을 막는다.

## Cloudtype 환경변수

**Cloudtype `iriro-server` 서비스의 환경변수/Secret 입력창**에 설정한다. 표의 `<...>`는 실제 값으로 바꾼다. 비밀번호와 키는 Git/문서/프론트 환경변수에 넣지 않는다.

| 이름 | Cloudtype에 넣을 값 | 설명 |
| --- | --- | --- |
| `PORT` | `8080` | 공개 포트와 일치 |
| `DB_URL` | `jdbc:mariadb://<DB 외부 호스트>:<외부 포트>/iriro` | 백엔드가 DB와 다른 Cloudtype 계정에 있으므로 DB의 외부 연결 주소 사용 |
| `DB_USERNAME` | `<iriro-db의 기존 DB 사용자>` | 현재 로컬 `.env`의 사용자와 동일한 계정인지 확인 |
| `DB_PASSWORD` | `<iriro-db의 기존 DB 비밀번호>` | Secret으로 입력 |
| `AI_SERVER_URL` | `https://port-0-iriro-ai-mnhfhlid34c7c977.sel3.cloudtype.app` | AI와 백엔드가 다른 Cloudtype 계정에 있으므로 공개 HTTPS 주소 사용. `/live/status`는 붙이지 않음 |
| `AUTH_JWT_SECRET` | `<32바이트 이상 랜덤 비밀키>` | 기존 사용자 토큰을 유지할 배포라면 기존 키와 동일하게 설정. 변경 시 기존 access/refresh 토큰 무효화 |
| `AUTH_ACCESS_TOKEN_TTL_SECONDS` | `1800` | 30분, 현재 로컬 설정값 |
| `AUTH_REFRESH_TOKEN_TTL_SECONDS` | `2592000` | 30일, 현재 로컬 설정값 |
| `KAKAO_APP_ID` | `<카카오 앱의 숫자 앱 ID>` | 기존 제공자 토큰 검증과 서버 시작에 필요. REST API 키와 다른 값 |
| `OAUTH_CALLBACK_BASE_URL` | `https://port-0-iriro-server-mwdjk6is6e6c1b09.sel3.cloudtype.app` | OAuth 서버 콜백의 기준 HTTPS 호스트. 끝에 `/api/v1`을 붙이지 않음 |
| `OAUTH_ALLOWED_REDIRECT_PREFIXES` | `exp://,cheonbyeon://` 또는 생략 | 앱 복귀 주소 허용 접두어. 기본값도 동일 |
| `KAKAO_REST_API_KEY` | `<카카오 REST API 키>` | 카카오 인가·토큰 교환용. Secret으로 입력 |
| `KAKAO_CLIENT_SECRET` | `<카카오 Client Secret>` 또는 생략 | 카카오 콘솔에서 Client Secret을 사용하면 필수. Secret으로 입력 |
| `NAVER_CLIENT_ID` | `<네이버 Client ID>` | 네이버 인가·토큰 교환용. Secret으로 입력 |
| `NAVER_CLIENT_SECRET` | `<네이버 Client Secret>` | 네이버 토큰 교환용 필수. Secret으로 입력 |
| `ACTIVITY_ALLOW_SIMULATION` | `false` | 운영 기본값. 시연을 허용할 별도 테스트 배포에서만 `true` |
| `WEATHER_API_URL` | `<기상청 API URL>` 또는 생략 | 생략 시 기본 빈 값. 실제 날씨 연결에는 필요 |
| `WEATHER_API_KEY` | `<기상청 API 키>` 또는 생략 | 실제 날씨 연결에는 필요. Secret으로 입력 |
| `SPRING_PROFILES_ACTIVE` | **설정하지 않음** | `local`을 배포 서버에 적용하면 Swagger와 시연 모드가 켜짐 |
| `SQL_LOG_LEVEL` | **설정하지 않음** | local 프로필에서만 참조 |

`DB_URL`은 JDBC 형식이어야 한다. 현재 백엔드는 DB·AI와 다른 Cloudtype 계정이므로 `iriro-db:3306`, `iriro-ai:8000` 같은 내부 이름을 그대로 쓰지 않는다. DB의 **외부 연결 화면에서 호스트·포트를 확인**하고, DB 측 접속 허용 범위도 확인한다. `iriro`는 현재 로컬 JDBC URL에 지정된 DB 이름이다. 기존 DB의 테이블은 Flyway가 서버 시작 시 마이그레이션한다. 연결할 계정에는 필요한 DDL/DML 권한이 있어야 한다.

카카오·네이버 개발자 콘솔에는 각각 다음 HTTPS 주소를 등록한다.

- `https://port-0-iriro-server-mwdjk6is6e6c1b09.sel3.cloudtype.app/api/v1/auth/oauth/kakao/callback`
- `https://port-0-iriro-server-mwdjk6is6e6c1b09.sel3.cloudtype.app/api/v1/auth/oauth/naver/callback`

## 배포 후 프론트에 줄 값

Cloudtype가 발급한 **백엔드** HTTPS 호스트를 `<backend-host>`로 두면:

```dotenv
EXPO_PUBLIC_API_BASE_URL=https://<backend-host>/api/v1
```

AI 서버 URL을 프론트 API 주소로 쓰지 않는다. 백엔드 헬스 체크는 `https://<backend-host>/actuator/health`이고, 공개 API 확인은 `https://<backend-host>/api/v1/trails`이다. 프론트의 전체 요청/응답 계약은 [frontend-api-integration.md](frontend-api-integration.md)를 따른다.

## 검증 순서

1. 배포 로그에서 Java 21 실행, DB 접속, Flyway 마이그레이션 완료를 확인한다.
2. `GET /actuator/health`의 HTTP 200 및 `UP`을 확인한다.
3. `GET /api/v1/trails`로 DB에 저장된 하천길 목록을 확인한다.
4. 월촌교와 연결된 하천길의 `GET /api/v1/trails/{trailId}/status`에서 `dataSource`와 AI 수위 응답을 확인한다. `UNKNOWN`이면 AI 내부 연결·응답·관측소 코드를 점검한다.
5. 로그인 후 Bearer 토큰으로 `GET /api/v1/users/me`, `GET /api/v1/badges`를 호출한다.
6. 백엔드 HTTPS 호스트를 프론트 `EXPO_PUBLIC_API_BASE_URL`에 넣고 앱에서 같은 순서로 호출한다.

배포 전 로컬의 `docs/frontend-api-integration.md`는 아직 Git에 없는 문서다. Cloudtype이 Git 저장소를 가져오는 방식이라면 배포에 필요한 `Dockerfile`과 `.dockerignore`가 배포할 브랜치에 올라가 있어야 한다.
