# 모바일 프론트 연동 계약

기준: 이 저장소의 API 구현. 앱은 Expo/React Native 네이티브 빌드로 연결한다. API 경로는 `/api/v1`이다. 전체 엔드포인트와 배지 8종 계약은 [frontend-api-integration.md](frontend-api-integration.md)를 따른다.

## 접속 주소

| 실행 위치 | `EXPO_PUBLIC_API_BASE_URL` |
| --- | --- |
| Android 에뮬레이터 + 개발 PC의 백엔드 | `http://10.0.2.2:8080/api/v1` |
| iOS 시뮬레이터 + 같은 Mac의 백엔드 | `http://localhost:8080/api/v1` |
| 실기기 또는 배포 앱 | 해당 기기에서 접근 가능한 **HTTPS API 주소** + `/api/v1` |

백엔드 기본 포트는 `8080`이다. `PORT`를 설정하면 그 값이 우선한다. 실기기에서 `localhost`는 개발 PC를 가리키지 않는다. 실기기용 HTTPS 주소는 서버가 배포된 뒤 확정해 앱 환경변수에 넣는다. `EXPO_PUBLIC_` 값은 앱에 포함되므로 비밀키를 넣지 않는다.

서버 점검 주소는 `{API 호스트}/actuator/health`이며, `/api/v1` 바깥에 있다. 로컬 프로필(`SPRING_PROFILES_ACTIVE=local`)의 API 문서는 `{API 호스트}/swagger-ui/index.html`과 `{API 호스트}/v3/api-docs`에서 확인한다. 기본 프로필에서는 문서가 비활성화된다.

## CORS

네이티브 React Native의 HTTP 요청에는 브라우저 CORS 설정이 필요하지 않다. 따라서 모바일 연동을 위해 백엔드에 CORS 허용 주소를 추가하지 않는다. Expo Web 또는 WebView에서 API를 직접 호출하게 되면, 그 **웹 페이지의 정확한 Origin**을 별도로 받아 `/api/v1/**`에 허용한다. 모바일 앱 주소나 `localhost`를 임의로 CORS 목록에 넣지 않는다.

## 요청과 인증

- 요청/응답 본문: JSON, `Content-Type: application/json`.
- 성공 본문: `{ "code": "SUCCESS", "message": "...", "data": ... }`. `204` 응답에는 본문이 없다.
- 오류 본문: `{ "code": "오류코드", "message": "...", "errors": null }`. 프론트는 HTTP 상태와 `code`를 함께 처리한다.
- 이메일 회원가입: `POST /auth/signup`. 이어서 `POST /auth/email/login`으로 로그인한다.
- 카카오·네이버 로그인: 앱에서 제공자 토큰을 받은 뒤 `POST /auth/login`에 `{ "provider": "KAKAO" | "NAVER", "providerToken": "..." }`를 보낸다.
- 로그인 응답 `data.accessToken`을 보호 API의 `Authorization: Bearer <accessToken>` 헤더에 넣는다.
- 만료 시 `POST /auth/reissue`에 `{ "refreshToken": "..." }`를 보내고 반환된 **새 accessToken과 refreshToken을 함께 교체**한다. 로그아웃은 `POST /auth/logout`에 현재 refresh token을 보낸다. 이 API는 쿠키 인증을 사용하지 않는다.

## 활동 기록과 시연 모드

- `POST /activities`는 Bearer 헤더와 `Idempotency-Key: <UUID>` 헤더가 필수다. 네트워크 재시도에서는 같은 UUID와 같은 본문을 유지한다. 본문이 달라지면 새 UUID를 사용한다.
- 본문 필수값: `routeId`, `startPointId`, `activityType`(`WALK`/`RUN`/`BIKE`), 오프셋이 포함된 `startedAt`·`endedAt`, 양수 `elapsedSeconds`·`distanceMeters`, 2개 이상의 `trackPoints`(`latitude`, `longitude`, `recordedAt`). `routeId`와 `startPointId`는 코스 조회 API의 실제 값을 사용한다.
- 프론트는 `simulated`를 항상 Boolean으로 보낸다. 실제 활동은 `false`, 20배속 시연은 `true`다. 속도값이나 좌표로 시연 여부를 추측하지 말고 활동 시작 시 정한 모드를 사용한다.
- 시연 저장과 시연 기록 목록·상세 조회는 **로컬 프로필에서 기본 허용**한다. 기본 프로필에서는 `simulated: true` 저장이 거절되고 시연 기록도 조회되지 않는다. 시연 기능을 공유 테스트 서버에서 검증할 때는 해당 서버의 `ACTIVITY_ALLOW_SIMULATION=true`를 설정한다. 운영 환경에는 `false`를 유지한다.
- 저장 성공 `data.simulated`, `GET /activities`의 각 항목, `GET /activities/{activityId}`의 `simulated`를 그대로 사용한다. `true`이면 목록·상세에 **시연** 라벨을 표시한다. 주간·월간·마이·배지 실적은 백엔드가 시연 기록을 제외한다.
- 목록의 `month`는 `YYYY-MM`, 주간 통계의 `date`는 `YYYY-MM-DD`, 월간 통계의 `month`는 `YYYY-MM`이다. 생략 시 한국 시간 현재 기간을 사용한다.

## 푸시 알림

Expo 푸시 토큰을 받은 뒤 `POST /devices`에 Bearer 헤더와 `{ "installationId": "UUID", "platform": "IOS" | "ANDROID", "expoPushToken": "ExpoPushToken[...]" }`를 보낸다. 앱 설치별 `installationId`는 재등록에도 동일하게 유지한다. 반환된 `data.deviceId`는 해제 시 `DELETE /devices/{deviceId}`에 사용한다.
