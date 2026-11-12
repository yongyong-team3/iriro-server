# 이리로 프론트 전체 API 연동 가이드

기준: 이 저장소의 현재 백엔드 구현, 2026-11-12. Expo/React Native 앱에서 바로 연결할 수 있도록 **주소 외에 필요한 요청 형식, 응답 필드, 호출 순서, 예외 처리**를 정리했다. 경로와 필드는 백엔드 코드 기준이며, 예시 값은 실제 서버 데이터가 아니다.

## 1. 연동 시작 전 5가지

1. `EXPO_PUBLIC_API_BASE_URL`에 **호스트 + `/api/v1`**까지 넣는다. 예: `https://<배포-호스트>/api/v1`. 아래 표의 경로에는 `/api/v1`을 다시 붙이지 않는다.
2. 네이티브 앱의 로그인 결과 `data.accessToken`을 저장하고 보호 API에 `Authorization: Bearer <accessToken>`을 붙인다. `refreshToken`은 재발급용이다.
3. 성공 본문은 `{ "code": "SUCCESS", "message": "...", "data": ... }`, 오류 본문은 `{ "code": "...", "message": "...", "errors": null }` 형태다. **HTTP 상태와 본문 `code`를 함께 확인**한다. `204`는 본문이 없다.
4. 거리 값의 단위는 **m**, 소요 시간은 **초**, 위치 좌표는 **위도(latitude), 경도(longitude)**다. GeoJSON `coordinates`는 반대로 **[경도, 위도]**다. 날짜와 시각은 별도 설명이 없는 한 오프셋이 있는 ISO 8601 문자열이다.
5. `401`이면 토큰 재발급 후 원 요청을 한 번 재시도한다. 재발급도 실패하면 로그인으로 보낸다. `POST /auth/reissue` 응답의 **access/refresh 토큰 둘 다** 교체한다.

### 주소와 환경

| 실행 위치 | `EXPO_PUBLIC_API_BASE_URL` 예시 |
| --- | --- |
| Android 에뮬레이터 + PC 서버 | `http://10.0.2.2:8080/api/v1` |
| iOS 시뮬레이터 + 같은 Mac 서버 | `http://localhost:8080/api/v1` |
| 실기기/배포 앱 | `https://<실기기에서 접근 가능한 호스트>/api/v1` |

`localhost`는 실기기에서 개발 PC를 뜻하지 않는다. 서버 기본 포트는 `8080`이며 배포 시 `PORT` 설정이 우선한다. 헬스 체크는 `{호스트}/actuator/health`로 `/api/v1` 밖에 있다. Swagger는 **local 프로필에서만** `{호스트}/swagger-ui/index.html` 및 `/v3/api-docs`로 열린다. `EXPO_PUBLIC_` 값은 앱 번들에 들어가므로 비밀키를 넣지 않는다. React Native 네이티브 요청에는 브라우저 CORS 설정이 필요 없다. Expo Web/WebView에서 호출하면 해당 웹 Origin의 CORS 허용이 별도로 필요하다.

### 공통 호출 예시 (TypeScript)

```ts
const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL?.replace(/\/$/, "");
if (!API_BASE_URL) throw new Error("EXPO_PUBLIC_API_BASE_URL 설정 필요");

type ApiSuccess<T> = { code: "SUCCESS"; message: string; data: T };
type ApiError = { code: string; message: string; errors: null | unknown[] };

async function api<T>(
  path: string,
  options: { method?: string; token?: string; body?: unknown; headers?: Record<string, string> } = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers: {
      ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...options.headers,
    },
    ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
  });
  if (response.status === 204) return undefined as T;
  const json: ApiSuccess<T> | ApiError = await response.json();
  if (!response.ok || json.code !== "SUCCESS") {
    throw Object.assign(new Error(json.message), { status: response.status, code: json.code });
  }
  return json.data;
}
```

토큰 재발급과 재시도는 앱의 공통 네트워크 계층에서 처리한다. 로그인/재발급 API 자체에 무한 재시도를 걸지 않는다. 사진 업로드에는 JSON 헬퍼 대신 `FormData`를 사용하고 `Content-Type`의 multipart boundary는 런타임에 맡긴다.

## 2. API 한눈에 보기

`🔒`는 access token 필요, `공개`는 불필요하다. 모든 경로는 `EXPO_PUBLIC_API_BASE_URL` 뒤에 이어 붙인다.

| 기능 | 메서드·경로 | 인증 | 성공 | 핵심 결과 |
| --- | --- | --- | --- | --- |
| 이메일 가입 | `POST /auth/signup` | 공개 | 201 | `data.memberId` |
| 이메일 로그인 | `POST /auth/email/login` | 공개 | 200 | `data.accessToken`, `refreshToken`, `member` |
| 카카오·네이버 로그인 | `POST /auth/login` | 공개 | 200 | 위와 같음 |
| 토큰 재발급 | `POST /auth/reissue` | 공개 | 200 | 새 토큰 2개 |
| 로그아웃 | `POST /auth/logout` | 공개 | 204 | 본문 없음 |
| 하천길 목록 | `GET /trails` | 공개 | 200 | `data.trails` |
| 하천길 상세 | `GET /trails/{trailId}` | 공개 | 200 | 하천길·GeoJSON |
| 하천길 코스 | `GET /trails/{trailId}/routes` | 공개 | 200 | `data.routes` |
| 하천 수위·예측 | `GET /trails/{trailId}/status` | 공개 | 200 | 상태·예측·출처 |
| 코스 상세 | `GET /routes/{routeId}` | 공개 | 200 | 코스·출발점·GeoJSON |
| 우회 경로 | `GET /routes/{routeId}/detours` | 공개 | 200 | `data.detours` |
| 시작 전 확인 | `GET /routes/{routeId}/availability` | 공개 | 200 | `startAllowed`, 경고 |
| 현재 날씨 | `GET /weather/current` | 공개 | 200 | 기온·날씨 |
| 과거 수위 | `GET /water-levels/history` | 공개 | 200 | 저장된 스냅샷 |
| 활동 저장 | `POST /activities` | 🔒 | 201 | `activityId`, `favoriteAdded`, `simulated` |
| 활동 목록 | `GET /activities` | 🔒 | 200 | `data.content`, 페이지 |
| 활동 상세 | `GET /activities/{activityId}` | 🔒 | 200 | 경로·페이스 |
| 주간 통계 | `GET /activities/stats/weekly` | 🔒 | 200 | 일별·최근 활동 |
| 월간 통계 | `GET /activities/stats/monthly` | 🔒 | 200 | 활동 날짜·연속 일수 |
| 즐겨찾기 목록 | `GET /favorites` | 🔒 | 200 | `data.favorites` |
| 즐겨찾기 추가 | `POST /favorites` | 🔒 | 201 또는 200 | `favoriteId`, `routeId` |
| 즐겨찾기 삭제 | `DELETE /favorites/{favoriteId}` | 🔒 | 204 | 본문 없음 |
| 배지 목록 | `GET /badges` | 🔒 | 200 | 8종·획득·진행도 |
| 내 정보 | `GET /users/me` | 🔒 | 200 | 프로필·요약 수치 |
| 프로필 수정 | `PATCH /users/me` | 🔒 | 200 | 변경된 프로필 |
| 닉네임 중복 | `GET /users/nickname-availability` | 공개 | 200 | `available` |
| 회원 탈퇴 | `DELETE /users/me` | 🔒 | 204 | 본문 없음 |
| 프로필 사진 업로드 | `POST /users/me/photo` | 🔒 | 200 | 상대 URL |
| 프로필 사진 기본값 | `DELETE /users/me/photo` | 🔒 | 204 | 본문 없음 |
| 프로필 사진 조회 | `GET /users/{memberId}/photo` | 공개 | 200 | 이미지 바이너리 |
| 알림 목록 | `GET /notifications` | 🔒 | 200 | `data.content`, 페이지 |
| 알림 읽음 | `PATCH /notifications/{notificationId}/read` | 🔒 | 200 | `readAt` |
| 알림 설정 조회 | `GET /notification-settings` | 🔒 | 200 | `recoveryForecastEnabled` |
| 알림 설정 변경 | `PATCH /notification-settings` | 🔒 | 200 | 변경된 설정 |
| 기기 등록/갱신 | `POST /devices` | 🔒 | 201 또는 200 | `deviceId` |
| 기기 해제 | `DELETE /devices/{deviceId}` | 🔒 | 204 | 본문 없음 |

## 3. 인증·회원

### 이메일 가입·로그인·소셜 로그인

```http
POST /auth/signup
Content-Type: application/json

{"email":"user@example.com","password":"abc12345","memberName":"용용이"}
```

가입 응답 `data`는 `{ "memberId": 1 }`이며 **토큰을 주지 않는다**. 이어서 이메일 로그인한다. 이메일은 공백 없는 일반적인 이메일 형식, 최대 256자다. 비밀번호는 영문과 숫자를 각각 하나 이상 포함한 8~64자이며 서버의 bcrypt 입력 제한인 UTF-8 **72바이트 이하**여야 한다. 닉네임은 한글·영문·숫자만 허용하며 2~12자다. 영문 닉네임과 이메일의 중복 판정은 대소문자를 구분하지 않는다.

```http
POST /auth/email/login
Content-Type: application/json

{"email":"user@example.com","password":"abc12345"}
```

카카오·네이버는 앱에서 제공자 **access token**을 받은 뒤 다음처럼 보낸다. 제공자 SDK 로그인 자체는 프론트가 수행한다.

```http
POST /auth/login
Content-Type: application/json

{"provider":"KAKAO","providerToken":"<제공자-access-token>"}
```

두 로그인 응답의 `data` 형태:

```json
{
  "accessToken": "<JWT>", "refreshToken": "<refresh-token>", "tokenType": "Bearer",
  "accessTokenExpiresInSeconds": 1800, "refreshTokenExpiresInSeconds": 2592000,
  "isNewMember": false, "requiresNicknameSetup": false,
  "member": { "memberId": 1, "memberName": "용용이", "profileImageUrl": null }
}
```

TTL 숫자는 **예시**이며 서버 설정값을 사용한다. `requiresNicknameSetup`이 참이면 프로필 닉네임 설정 화면으로 보낸다. 이메일·소셜 로그인 모두 `member.memberId`를 사용자 ID로 쓴다.

### 토큰 재발급·로그아웃

```http
POST /auth/reissue
Content-Type: application/json

{"refreshToken":"<현재-refresh-token>"}
```

응답 `data`에는 `tokenType`, **새** `accessToken`, **새** `refreshToken`, 각 TTL 초가 있다. refresh token은 회전되므로 이전 값을 계속 쓰면 재발급에 실패한다. `POST /auth/logout`에도 같은 `{ "refreshToken": "..." }` 본문을 보내며 `204`에는 JSON 파싱을 하지 않는다.

### 마이 화면·프로필

`GET /users/me`의 `data`:

```ts
type MyInfo = {
  memberId: number; memberName: string; email: string | null;
  profileImageUrl: string | null;
  totalActivityCount: number; totalDistanceMeters: number;
  earnedBadgeCount: number; unreadNotificationCount: number;
};
```

`profileImageUrl`은 `/api/v1/users/{memberId}/photo` 같은 **API 호스트 기준 상대 경로**다. 이미지에는 `{호스트}${profileImageUrl}`을 사용한다. `null`이면 앱 기본 이미지를 표시한다. `email`은 소셜 계정에서 없을 수 있으므로 null 처리를 한다. 마이 화면의 배지 수는 `earnedBadgeCount`, 배지 상세 화면에는 `GET /badges`를 사용한다.

| 요청 | 보낼 값 | 반환 `data` |
| --- | --- | --- |
| `GET /users/nickname-availability?memberName=용용이` | 닉네임 문자열, 인증 불필요 | `{ memberName, available: boolean }` |
| `PATCH /users/me` | `{ "memberName": "새닉네임" }` | `{ memberId, memberName, profileImageUrl }` |
| `POST /users/me/photo` | multipart `photo` 파일 (PNG/JPEG) | `{ profileImageUrl }` |
| `DELETE /users/me/photo` | 없음 | 204 |
| `GET /users/{memberId}/photo` | 없음, 인증 불필요 | `image/png` 또는 `image/jpeg` 바이너리 |
| `DELETE /users/me` | 없음 | 204, 계정 탈퇴 |

사진은 **2MB 이하, 가로·세로 각 4096px 이하**로 업로드한다. 업로드 요청의 part 이름은 반드시 `photo`다. 탈퇴 성공 후에는 앱의 저장 토큰과 사용자 정보를 비운다. 닉네임 중복은 `available`을 사용하며, 실제 변경 시 409 중복 오류도 처리한다.

## 4. 하천길·코스·날씨·수위

### 지도/검색 호출 순서

1. `GET /trails?latitude=37.2794&longitude=127.2274&query=월촌`으로 하천길 검색. `latitude`와 `longitude`는 **둘 다 보내거나 둘 다 생략**한다. `query`는 선택이다.
2. 선택한 `trailId`로 `GET /trails/{trailId}`와 `GET /trails/{trailId}/routes?activityType=WALK` 호출. 활동 유형 필터는 `WALK`, `RUN`, `BIKE` 또는 생략이다.
3. 선택한 `routeId`로 `GET /routes/{routeId}` 호출하여 `startPoints`를 받는다. 선택한 출발점의 **실제 `startPointId`**를 사용한다.
4. 시작 버튼 직전 `GET /routes/{routeId}/availability?startPointId={startPointId}` 호출. `warnings`를 사용자에게 보여준다.
5. 우회 안내가 필요하면 `GET /routes/{routeId}/detours` 호출한다. 우회로가 없으면 `detours: []`다.

### 응답 필드

| API | `data` 필드 |
| --- | --- |
| `GET /trails` | `trails: [{trailId, name, sectionName, latitude, longitude, distanceMeters, lengthMeters, waterStatus, dataSource}]`, `responseGeneratedAt`. 위치를 안 보내면 `distanceMeters`는 null. |
| `GET /trails/{trailId}` | `trailId`, `name`, `sectionName`, `description`, `lengthMeters`, `latitude`, `longitude`, `geometry` (GeoJSON). |
| `GET /trails/{trailId}/routes` | `trailId`, `routes: [{routeId, name, activityTypes, distanceMeters, estimatedDurationSeconds, geometry, startPoints}]`. `startPoints` 항목은 `{startPointId, name, latitude, longitude}`. |
| `GET /routes/{routeId}` | `routeId`, `trailId`, `name`, `activityTypes`, `distanceMeters`, `estimatedDurationSeconds`, `geometry`, `startPoints`, `waterStatus`. |
| `GET /routes/{routeId}/detours` | `routeId`, `detours: [{detourId, name, distanceMeters, extraDistanceMeters, geometry}]`. |
| `GET /routes/{routeId}/availability` | `routeId`, `startPointId`, `startAllowed`, `waterStatus`, `dataSource`, `warnings: string[]`, `checkedAt`. |

`waterStatus`는 `NORMAL`, `HIGH_WATER`, `UNKNOWN`을 처리한다. `availability`는 현재 구현에서 수위가 높거나 알 수 없어도 `startAllowed: true`이고 경고를 반환한다. 따라서 **`warnings`를 무시한 채 안전 판정처럼 표시하지 않는다.** `dataSource`는 `LIVE`, `REPLAY`, `UNAVAILABLE`이 가능하다. `REPLAY`는 시연 데이터이며 운영 환경에서는 허용되지 않는다. GeoJSON은 JSON 객체로 받으며 지도 라이브러리 좌표 순서는 `[longitude, latitude]`다.

### 상세 수위 상태

`GET /trails/{trailId}/status`의 `data` 필드:

```ts
type TrailStatus = {
  trailId: number; stationName: string | null;
  dataSource: "LIVE" | "REPLAY" | "UNAVAILABLE";
  dataStatus: "OK" | "PARTIAL" | "STALE" | "MISSING";
  forecastModelVersion: string | null; recoveryModelVersion: string | null;
  waterStatus: "NORMAL" | "HIGH_WATER" | "UNKNOWN";
  currentWaterLevelMeters: number | null; observedAt: string | null;
  predictedWaterLevelMeters: number | null; forecastTargetAt: string | null;
  trend: string | null; recoveryStatus: string | null;
  recoveryEtaAtLeastMinutes: number | null;
  officialControlStatus: string | null; notice: string | null;
  responseGeneratedAt: string;
};
```

관측소 미연결, AI 장애, 오래된 실측이면 `UNKNOWN`과 null 필드가 올 수 있다. `notice`를 표시하고 null 수치를 숨긴다. `officialControlStatus`가 null인 현재 응답을 공식 통제 여부로 추정하지 않는다.

### 날씨·과거 수위

`GET /weather/current?latitude=37.2794&longitude=127.2274`: 좌표는 둘 다 보내거나 생략한다. 생략 시 월촌교 좌표를 쓴다. `data = { areaName, temperatureCelsius: number|null, condition, observedAt: string|null, responseGeneratedAt }`. `condition`은 `SUNNY`, `CLOUDY`, `RAINY`, `SNOWY`, `UNKNOWN`이다. 외부 기상 데이터가 없으면 온도·관측 시각이 null일 수 있다.

`GET /water-levels/history?at=2026-11-12T12%3A05%3A00%2B09%3A00&stationCode=1016607`: `at`은 **오프셋 포함 필수**이고 URL 인코딩한다. `stationCode` 생략 시 `1016607`(월촌교). 요청 시각 이전 10분 내 저장된 스냅샷만 반환하며, 없으면 `404 HISTORICAL_SNAPSHOT_NOT_FOUND`다. `data`에는 `stationCode`, `requestedAt`, `observedAt`, `currentWaterLevelMeters`, `waterStatus`, `predictedWaterLevelMeters`, `forecastTargetAt`, `trend`, `recoveryStatus`, `recoveryEtaAtLeastMinutes`, `aiEvaluatedAt`, `snapshotRecordedAt`, `forecastModelVersion`, `recoveryModelVersion`, `dataSource`, `dataStatus`, `officialControlStatus`가 있다. 과거 조회에 현재 상태 API 값을 대신 쓰지 않는다.

## 5. 활동 저장·기록·통계

### 활동 저장: 앱에서 가장 먼저 맞춰야 할 요청

```http
POST /activities
Authorization: Bearer <accessToken>
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "routeId": 1,
  "startPointId": 1,
  "activityType": "WALK",
  "startedAt": "2026-11-12T09:00:00+09:00",
  "endedAt": "2026-11-12T09:20:00+09:00",
  "elapsedSeconds": 1200,
  "distanceMeters": 1500.5,
  "trackPoints": [
    {"latitude": 37.2794, "longitude": 127.2274, "recordedAt": "2026-11-12T09:00:00+09:00"},
    {"latitude": 37.2800, "longitude": 127.2280, "recordedAt": "2026-11-12T09:20:00+09:00"}
  ],
  "saveAsFavorite": false,
  "simulated": false
}
```

예시 ID는 실제 서버의 코스/출발점 ID로 교체한다. `activityType`은 해당 코스의 `activityTypes`에 포함되어야 한다. `routeId`와 `startPointId`는 서로 연결된 값이어야 한다. 시작·종료·GPS 기록 시각은 오프셋 포함 ISO 8601로 보내고 종료는 시작보다 늦어야 한다. `elapsedSeconds`와 `distanceMeters`는 양수, `trackPoints`는 최소 2개다. 위도 범위는 -90~90, 경도는 -180~180이다.

`Idempotency-Key`는 **활동 저장 한 건당 UUID**다. 네트워크 실패로 같은 저장을 재시도할 때는 **같은 UUID와 같은 본문**을 보낸다. 본문이 달라졌다면 새 UUID를 사용한다. 같은 키에 다른 본문을 보내면 `409 IDEMPOTENCY_CONFLICT`다. 성공은 신규 저장과 동일 요청 재시도 모두 `201`이고 `data = { activityId, favoriteAdded, simulated }`이다. `favoriteAdded`는 `saveAsFavorite: true`여도 이미 즐겨찾기되어 있으면 false다.

`simulated`는 프론트가 활동 시작 시 선택한 모드에 따라 **명시적 Boolean**으로 보낸다. 실제 활동은 false, 20배속 시연은 true다. 시연 기록 저장과 조회는 local 프로필에서 기본 허용하며, 공유 테스트 서버에서는 `ACTIVITY_ALLOW_SIMULATION=true` 설정이 필요하다. 기본/운영 환경에서는 true 저장이 `400 INVALID_REQUEST`로 거절되고 시연 기록 조회도 제외된다. 실제 기록만 배지·마이·주간·월간 통계에 반영된다. 시연 여부를 속도나 좌표로 추정하지 않는다.

### 기록 목록·상세

| API | 요청 | `data` |
| --- | --- | --- |
| `GET /activities` | `month=YYYY-MM` 선택, `activityType=WALK\|RUN\|BIKE` 선택, `page=0` 기본, `size=20` 기본(1~100) | `{content, page, size, totalElements, totalPages, hasNext}`. 최신 순. |
| `GET /activities/{activityId}` | 본인 활동 ID | `activityId`, `routeId`, `routeName`, `activityType`, `startedAt`, `endedAt`, `distanceMeters`, `elapsedSeconds`, `simulated`, `averagePaceSecondsPerKm`, `geometry`. |

목록 `content` 항목은 `{ activityId, routeId, routeName, activityType, startedAt, distanceMeters, elapsedSeconds, simulated }`이다. 월 생략 시 **한국 시간 현재 월**이다. 상세 `geometry`는 GeoJSON 형태의 `{ type: "LineString", coordinates: [[longitude, latitude], ...] }` 또는 null이다. `averagePaceSecondsPerKm`는 초/km다. 시연이 허용된 환경에서는 목록·상세 `simulated: true`에 시연 라벨을 표시한다.

### 통계

| API | 요청 | `data` |
| --- | --- | --- |
| `GET /activities/stats/weekly` | `date=YYYY-MM-DD` 선택, 생략 시 한국 시간 오늘 | `weekStartDate`, `weekEndDate`, `activityCount`, `distanceMeters`, `elapsedSeconds`, `daily`, `recentActivities`. |
| `GET /activities/stats/monthly` | `month=YYYY-MM` 선택, 생략 시 한국 시간 현재 월 | `month`, `activityCount`, `distanceMeters`, `elapsedSeconds`, `activityDates`, `currentStreakDays`, `longestStreakDays`. |

주간은 한국 시간 **월요일~일요일**, `daily`에는 7일 각각 `{ date, activityCount, distanceMeters, elapsedSeconds }`가 있다. `recentActivities`는 해당 주 최신 최대 3건이며 `{ activityId, routeName, activityType, distanceMeters, startedAt }`이다. 월간 `activityDates`는 활동한 날짜의 `YYYY-MM-DD` 배열이다. `currentStreakDays`와 `longestStreakDays`는 **선택 월이 아니라 전체 실제 기록** 기준이다. 통계는 시연 기록을 포함하지 않는다.

## 6. 즐겨찾기

| API | 요청 | `data`/처리 |
| --- | --- | --- |
| `GET /favorites` | Bearer | `{ favorites: [{favoriteId, routeId, routeName, trailId, trailName, distanceMeters, geometry}] }`. `geometry`는 GeoJSON LineString, 좌표 `[경도, 위도]`. |
| `POST /favorites` | `{ "routeId": 1 }` | `{ favoriteId, routeId }`. 새 항목은 201, 이미 저장된 항목은 200과 기존 ID. |
| `DELETE /favorites/{favoriteId}` | 목록/추가 결과의 **favoriteId** | 204, 본문 없음. |

삭제 URL에는 `routeId`가 아니라 `favoriteId`를 넣는다. 활동 저장의 `saveAsFavorite`를 사용해 추가했다면 저장 후 목록을 다시 조회하여 ID와 표시 상태를 갱신한다.

## 7. 배지 8종

`GET /badges`는 본인의 8종 전체를 열거한다. `data = { earnedCount, totalCount: 8, badges: [...] }`. 각 항목은 `{ code, name, target, progress, earned, earnedAt }`이다. `earnedAt`은 획득 시 `2026-11-11T10:30:00+09:00` 같은 문자열, 미획득 시 null이다. **획득 표시에는 `progress >= target` 계산 대신 `earned`를 사용**한다. `code`를 이미지·문구 매핑 키로 쓰고 배열 순서는 화면 고정 순서로 가정하지 않는다.

| `code` | `name` | `target` | 현재 진행도 계산 |
| --- | --- | ---: | --- |
| `COURSE_FINISHER` | 코스 완주자 | 3 | 판정 데이터 미연결, 현재 0 |
| `RIVER_BRIDGE` | 하천 브릿지 | 10 | 판정 데이터 미연결, 현재 0 |
| `RIVER_WALKWAY` | 하천 산책로 | 15 | 판정 데이터 미연결, 현재 0 |
| `WALK_LOVER` | 산책 러버 | 20 | 실제 걷기(`WALK`) 기록 수 |
| `YONGIN_CITY_EXPLORER` | 용인 도시탐험 | 3 | **3회 이상 활동한 서로 다른 코스 수** |
| `BIKE_RIDER` | 자전거 라이더 | 100000 | 실제 자전거(`BIKE`) 누적 거리, m |
| `CONSISTENT_ACTIVIST` | 꾸준한 활동가 | 30 | 활동한 서로 다른 한국 날짜 수 |
| `YONGIN_DRAGON` | 용인 드래곤 | 200000 | 모든 유형의 실제 활동 누적 거리, m |

처음 3종은 현재 백엔드가 진행도를 계산하거나 새로 지급하지 않는다. 화면은 잠금 상태를 그대로 표시하고, 0 진행도를 버그로 간주하지 않는다. 나머지 5종은 **실제 활동 저장 시** 지급을 판정한다. 진행도가 목표를 넘어갈 수 있으므로 UI 막대만 `min(progress / target, 1)`로 제한한다. 기존 3종 코드 `FIRST_ACTIVITY`, `FIVE_ACTIVITIES`, `TEN_KM`은 새 배지로 승계하지 않고 응답에 등장하지 않는다.

## 8. 알림·푸시

| API | 요청 | `data`/처리 |
| --- | --- | --- |
| `GET /notifications` | `unreadOnly=false` 기본, `page=0`, `size=20`(1~100) | `{content, page, size, totalElements, totalPages, hasNext}` |
| `PATCH /notifications/{notificationId}/read` | 본인 알림 ID, 본문 없음 | `{ notificationId, readAt }` |
| `GET /notification-settings` | Bearer | `{ recoveryForecastEnabled: boolean }` |
| `PATCH /notification-settings` | `{ "recoveryForecastEnabled": true }` | 변경된 `{ recoveryForecastEnabled }` |
| `POST /devices` | 아래 JSON | `{ deviceId }`, 신규 201/재등록 200 |
| `DELETE /devices/{deviceId}` | 등록 결과의 deviceId | 204 |

알림 `content` 항목: `{ notificationId, type, title, body, trailId, createdAt, readAt }`. 현재 알림 유형은 `RECOVERY_FORECAST`이며 `readAt: null`은 미읽음이다. `trailId`가 있으면 해당 하천길 상세로 이동할 수 있다. 알림 배지는 `GET /users/me`의 `unreadNotificationCount`로 표시하고 읽음 처리 후 내 정보/목록을 갱신한다.

Expo 푸시 토큰을 발급받은 뒤:

```json
{"installationId":"550e8400-e29b-41d4-a716-446655440000","platform":"IOS","expoPushToken":"ExpoPushToken[실제토큰]"}
```

`installationId`는 앱 설치별 UUID이며 재등록에도 **같은 값**을 사용한다. `platform`은 `IOS` 또는 `ANDROID`, 푸시 토큰은 `ExpoPushToken[...]` 또는 `ExponentPushToken[...]` 형식이다. `deviceId`는 해제에 사용한다. 앱이 토큰을 새로 받거나 사용자가 바뀌면 등록 상태를 갱신한다.

## 9. 화면별 최소 호출 흐름

| 화면/동작 | 호출 순서 |
| --- | --- |
| 로그인 후 | 로그인 → 토큰 보관 → `GET /users/me` → 필요 시 `POST /devices` |
| 지도 홈 | `GET /trails` + `GET /weather/current` → 선택 시 `GET /trails/{id}` + `GET /trails/{id}/status` |
| 코스 선택/시작 | `GET /trails/{id}/routes` → `GET /routes/{id}` → 출발점 선택 → `GET /routes/{id}/availability` → 경고 표시 |
| 활동 완료 | `POST /activities`(고정 UUID, GPS 포함) → `GET /activities/{activityId}` → `GET /badges` / `GET /users/me` 갱신 |
| 기록 화면 | `GET /activities?month=YYYY-MM` → 선택 시 상세 → 통계 탭에서 weekly/monthly 호출 |
| 즐겨찾기 | `GET /favorites` → 추가 `POST /favorites` 또는 해제 `DELETE /favorites/{favoriteId}` |
| 마이/배지 | `GET /users/me` → 배지 화면 `GET /badges` |
| 알림 | `GET /notifications` + `GET /notification-settings` → 읽음 처리 → 목록/카운트 재조회 |

## 10. 오류 처리와 확인 순서

| HTTP / `code` | 프론트 처리 |
| --- | --- |
| 400 / `INVALID_REQUEST`, `INVALID_START_POINT` | 입력/좌표/날짜/출발점 확인. API가 보낸 `message` 표시 가능. |
| 401 / `UNAUTHORIZED`, `INVALID_TOKEN`, `TOKEN_EXPIRED` | refresh token으로 재발급 후 원 요청 한 번 재시도. 실패 시 로그인. |
| 401 / `INVALID_CREDENTIALS`, `INVALID_PROVIDER_TOKEN`, `INVALID_REFRESH_TOKEN`, `EXPIRED_REFRESH_TOKEN` | 로그인 입력/제공자 토큰 확인 또는 재로그인. |
| 404 / `TRAIL_NOT_FOUND`, `ROUTE_NOT_FOUND`, `ACTIVITY_NOT_FOUND`, `NOTIFICATION_NOT_FOUND`, `HISTORICAL_SNAPSHOT_NOT_FOUND`, `PROFILE_PHOTO_NOT_FOUND`, `NOT_FOUND` | 목록 새로고침 또는 해당 화면의 빈/없음 상태 표시. |
| 409 / `DUPLICATE_EMAIL`, `DUPLICATE_MEMBER_NAME`, `IDEMPOTENCY_CONFLICT` | 중복 입력 안내 또는 활동 저장 키/본문 확인. |
| 413 / `PAYLOAD_TOO_LARGE` | 사진 크기 줄이기. |
| 5xx / `PROVIDER_UNAVAILABLE` 등 | 일시 오류 안내. 동일 활동 재시도에는 기존 `Idempotency-Key`와 본문 유지. |

연동 검증은 다음 순서면 실패 지점을 빨리 찾을 수 있다.

1. `{호스트}/actuator/health`가 응답하는지 확인한다. 배포 주소에는 `/api/v1`을 한 번만 붙인다.
2. 공개 `GET /trails` 또는 `GET /weather/current`로 네트워크·JSON 파싱을 확인한다.
3. 이메일/소셜 로그인 후 `GET /users/me`로 Bearer 전달을 확인한다.
4. `GET /trails/{trailId}/routes`에서 **실제** `routeId`·`startPointId`를 받아 활동 저장에 사용한다.
5. `POST /activities`를 저장하고 같은 키/본문으로 한 번 재시도해 같은 `activityId`인지 확인한다. 활동 상세·목록·통계·배지를 차례로 새로 조회한다.
6. `GET /badges`에서 8개 `code`, `earned`, 거리 단위(m)를 확인한다. 시연 기록이 통계/배지에 들어가지 않는지 확인한다.
7. `GET /favorites`, `GET /notifications`, `GET /notification-settings`와 프로필 사진 등 나머지 화면을 연결한다.

### 백엔드 소스 기준점

- 경로/입력: 각 기능의 `src/main/java/com/iriro/server/{auth,trail,weather,river,activity,favorite,badge,member,notification}/presentation`.
- 배지 코드·목표: `badge/domain/BadgeCode.java`; 실제 판정: `badge/application/BadgeService.java`.
- 공통 오류: `common/presentation/ApiExceptionHandler.java`; 인증 토큰 필터: `auth/infrastructure/JwtAccessTokenFilter.java`.
- 서버 설정과 시연 허용값: `src/main/resources/application.properties`, `application-local.properties`.

백엔드 배포 주소와 서버 환경 설정이 정해지면 이 문서의 `<배포-호스트>`만 실제 값으로 바꾸고, 프론트 환경변수에 반영하면 된다.
