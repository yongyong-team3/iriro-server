package com.iriro.server.activity.application;


import tools.jackson.databind.JsonNode;
import com.iriro.server.activity.presentation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;
import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.favorite.application.FavoriteService;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.infrastructure.RouteRepository;
import com.iriro.server.trail.infrastructure.RouteStartPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private static final Set<String> ACTIVITY_TYPES =
            Set.of("WALK", "RUN", "BIKE");

    private final ActivityRepository activityRepository;
    private final MemberRepository memberRepository;
    private final RouteRepository routeRepository;
    private final RouteStartPointRepository startPointRepository;

    private final FavoriteService favoriteService;
    private final ActivityHashGenerator hashGenerator;
    private final ObjectMapper objectMapper;

    /**
     * ACT-01 활동 기록 저장
     */
    @Transactional
    public ActivityCreateResponse createActivity(
            Long memberId,
            String idempotencyKey,
            ActivityCreateRequest request
    ) {

        // 1. 회원 확인
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        // 2. 요청 검증
        validateIdempotencyKey(idempotencyKey);
        validateRequest(request);

        // 3. 코스 확인
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_NOT_FOUND));

        // 3-1. 코스가 지원하는 활동 유형인지 확인
        // (DB 의 fk_activity_type 위반이 500 으로 올라가지 않도록 먼저 400 으로 거른다)
        if (!route.getActivityTypes().contains(request.getActivityType())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 4. 출발점 및 코스 소속 여부 확인
        if (!startPointRepository.existsByIdAndRouteId(
                request.getStartPointId(),
                request.getRouteId()
        )) {
            throw new CustomException(ErrorCode.START_POINT_NOT_FOUND);
        }

        // 5. 시연 여부 정규화
        boolean simulated = Boolean.TRUE.equals(request.getSimulated());

        // 6. 요청 해시 생성
        String requestHash = hashGenerator.generate(
                request,
                simulated
        );

        // 7. 기존 요청 확인
        Activity existing = activityRepository
                .findByMemberIdAndIdempotencyKey(
                        memberId,
                        idempotencyKey
                )
                .orElse(null);

        if (existing != null) {

            // 동일 키에 다른 요청 내용이면 충돌
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new CustomException(
                        ErrorCode.IDEMPOTENCY_CONFLICT
                );
            }

            // 동일 요청이면 기존 활동 반환
            return createResponse(
                    existing.getId(),
                    false,
                    existing.isSimulated()
            );
        }

        // 8. GPS 경로 JSON 변환
        String trackPointsJson;

        try {
            trackPointsJson = objectMapper.writeValueAsString(
                    request.getTrackPoints()
            );
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 9. 활동 Entity 생성
        Activity activity = Activity.builder()
                .memberId(memberId)
                .routeId(request.getRouteId())
                .startPointId(request.getStartPointId())
                .activityType(request.getActivityType())
                .startedAt(toUtc(request.getStartedAt()))
                .endedAt(toUtc(request.getEndedAt()))
                .elapsedSeconds(request.getElapsedSeconds())
                .distanceMeters(request.getDistanceMeters())
                .trackPointsJson(trackPointsJson)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .simulated(simulated)
                .build();

        // 10. 활동 저장
        Activity saved = activityRepository.saveAndFlush(activity);

        // 11. 즐겨찾기 추가
        boolean favoriteAdded = false;

        if (Boolean.TRUE.equals(request.getSaveAsFavorite())) {
            favoriteAdded = favoriteService.addFavorite(
                    memberId,
                    request.getRouteId()
            );
        }

        // 12. 응답 생성
        return createResponse(
                saved.getId(),
                favoriteAdded,
                simulated
        );
    }

    /**
     * 같은 Idempotency-Key 로 이미 저장된 활동이 있으면 그 결과를 반환한다.
     *
     * 동시에 같은 키로 요청이 들어와 한쪽이 유니크 제약 위반으로 실패했을 때,
     * ActivityCreateFacade 가 (실패한 트랜잭션이 끝난 뒤) 새 트랜잭션에서 호출한다.
     *
     * @return 기존 활동이 없으면 empty
     * @throws CustomException 같은 키에 다른 요청 내용이면 IDEMPOTENCY_CONFLICT
     */
    public Optional<ActivityCreateResponse> findExistingResult(
            Long memberId,
            String idempotencyKey,
            ActivityCreateRequest request
    ) {

        boolean simulated = Boolean.TRUE.equals(request.getSimulated());
        String requestHash = hashGenerator.generate(request, simulated);

        return activityRepository
                .findByMemberIdAndIdempotencyKey(memberId, idempotencyKey)
                .map(existing -> {

                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new CustomException(ErrorCode.IDEMPOTENCY_CONFLICT);
                    }

                    return createResponse(
                            existing.getId(),
                            false,
                            existing.isSimulated()
                    );
                });
    }

    /**
     * UUID 검증
     */
    private void validateIdempotencyKey(String key) {

        if (key == null || key.length() != 36) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 활동 데이터 검증
     */
    private void validateRequest(ActivityCreateRequest request) {

        if (request == null ||
                request.getRouteId() == null ||
                request.getStartPointId() == null ||
                request.getActivityType() == null ||
                request.getStartedAt() == null ||
                request.getEndedAt() == null ||
                request.getElapsedSeconds() == null ||
                request.getDistanceMeters() == null ||
                request.getTrackPoints() == null) {

            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        if (!ACTIVITY_TYPES.contains(request.getActivityType())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        if (!request.getStartedAt().isBefore(request.getEndedAt())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        if (request.getElapsedSeconds() <= 0 ||
                request.getDistanceMeters().compareTo(BigDecimal.ZERO) <= 0) {

            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        if (request.getTrackPoints().size() < 2) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        OffsetDateTime previousTime = null;

        for (ActivityCreateRequest.TrackPoint point : request.getTrackPoints()) {

            if (point == null ||
                    point.getLatitude() == null ||
                    point.getLongitude() == null ||
                    point.getRecordedAt() == null) {

                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }

            double latitude = point.getLatitude();
            double longitude = point.getLongitude();

            if (!Double.isFinite(latitude) ||
                    !Double.isFinite(longitude) ||
                    latitude < -90 || latitude > 90 ||
                    longitude < -180 || longitude > 180) {

                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }

            if (previousTime != null &&
                    point.getRecordedAt().isBefore(previousTime)) {

                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }

            previousTime = point.getRecordedAt();
        }
    }

    /**
     * OffsetDateTime -> UTC LocalDateTime
     */
    private LocalDateTime toUtc(OffsetDateTime dateTime) {

        return dateTime
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * 응답 생성
     */
    private ActivityCreateResponse createResponse(
            Long activityId,
            boolean favoriteAdded,
            boolean simulated
    ) {
        return new ActivityCreateResponse(
                activityId,
                favoriteAdded,
                simulated
        );
    }

    /**
     * ACT-02 활동 기록 목록 조회
     */
    public ActivityListResponse getActivities(
            Long memberId,
            String month,
            String activityType,
            int page,
            int size
    ) {

        // 1. 회원 확인
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        // 2. 요청 파라미터 검증
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 3. 활동 유형 검증
        if (activityType != null && !activityType.isBlank()) {

            if (!ACTIVITY_TYPES.contains(activityType)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
        } else {
            activityType = null;
        }

        // 4. 조회 월 설정
        YearMonth yearMonth;

        try {
            yearMonth = (month == null || month.isBlank())
                    ? YearMonth.now(ZoneId.of("Asia/Seoul"))
                    : YearMonth.parse(month);

        } catch (DateTimeException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 5. 한국 시간 기준 월 시작과 다음 달 시작
        // DB에는 UTC LocalDateTime으로 저장되어 있으므로 UTC로 변환
        ZoneOffset kst = ZoneOffset.ofHours(9);

        LocalDateTime start = yearMonth
                .atDay(1)
                .atStartOfDay()
                .atOffset(kst)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime end = yearMonth
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay()
                .atOffset(kst)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // 6. 최신순 정렬 및 페이징
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("startedAt"),
                        Sort.Order.desc("id")
                )
        );

        // 7. 활동 목록 조회
        Page<Activity> activities;

        if (activityType == null) {

            activities = activityRepository
                    .findByMemberIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                            memberId,
                            start,
                            end,
                            pageable
                    );

        } else {

            activities = activityRepository
                    .findByMemberIdAndActivityTypeAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                            memberId,
                            activityType,
                            start,
                            end,
                            pageable
                    );
        }

        // 8. 코스 이름을 포함한 응답 DTO 변환
        Page<ActivityResponse> responsePage = activities.map(activity -> {

            String routeName = routeRepository.findById(activity.getRouteId())
                    .map(route -> route.getName())
                    .orElseThrow(() ->
                            new CustomException(ErrorCode.ROUTE_NOT_FOUND)
                    );

            return ActivityResponse.from(activity, routeName);
        });

        // 9. 페이징 응답 반환
        return ActivityListResponse.from(responsePage);
    }
    /**
     * ACT-03 활동 기록 상세 조회
     */
    public ActivityDetailResponse getActivityDetail(
            Long memberId,
            Long activityId
    ) {

        // 1. 활동 ID와 회원 ID로 본인 기록 조회
        Activity activity = activityRepository
                .findByIdAndMemberId(activityId, memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.ACTIVITY_NOT_FOUND)
                );

        // 2. 코스 이름 조회
        String routeName = routeRepository.findById(activity.getRouteId())
                .map(route -> route.getName())
                .orElseThrow(() ->
                        new CustomException(ErrorCode.ROUTE_NOT_FOUND)
                );

        // 3. 저장된 GPS 경로를 GeoJSON으로 변환
        ActivityDetailResponse.Geometry geometry =
                convertToGeometry(activity.getTrackPointsJson());

        // 4. 상세 응답 생성
        return ActivityDetailResponse.from(
                activity,
                routeName,
                geometry
        );
    }

    /**
     * GPS 경로 JSON -> GeoJSON LineString
     *
     * 좌표 순서:
     * [longitude, latitude]
     *
     * 점이 2개 미만이면 null 반환
     */
    private ActivityDetailResponse.Geometry convertToGeometry(
            String trackPointsJson
    ) {

        try {

            JsonNode points = objectMapper.readTree(trackPointsJson);

            // 배열이 아니거나 점이 2개 미만이면 geometry 없음
            if (points == null || !points.isArray() || points.size() < 2) {
                return null;
            }

            List<List<Double>> coordinates = new ArrayList<>();

            for (JsonNode point : points) {

                JsonNode latitudeNode = point.get("latitude");
                JsonNode longitudeNode = point.get("longitude");

                if (latitudeNode == null ||
                        longitudeNode == null ||
                        !latitudeNode.isNumber() ||
                        !longitudeNode.isNumber()) {

                    return null;
                }

                double latitude = latitudeNode.asDouble();
                double longitude = longitudeNode.asDouble();

                // GeoJSON 좌표 순서는 경도, 위도
                coordinates.add(
                        List.of(longitude, latitude)
                );
            }

            // 유효한 좌표가 2개 미만이면 null
            if (coordinates.size() < 2) {
                return null;
            }

            return ActivityDetailResponse.Geometry.builder()
                    .type("LineString")
                    .coordinates(coordinates)
                    .build();

        } catch (Exception e) {

            // 저장된 GPS JSON이 손상된 경우 geometry를 제공하지 않음
            return null;
        }
    }
    /**
     * ACT-04 주간 활동 통계 조회
     */
    public ActivityWeeklyStatsResponse getWeeklyStats(
            Long memberId,
            String date
    ) {

        // 1. 회원 확인
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        // 2. 조회 기준일 설정
        LocalDate targetDate;

        try {
            targetDate = (date == null || date.isBlank())
                    ? LocalDate.now(ZoneId.of("Asia/Seoul"))
                    : LocalDate.parse(date);

        } catch (DateTimeException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 3. 해당 주의 월요일과 일요일 계산
        LocalDate weekStartDate = targetDate.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDate weekEndDate = weekStartDate.plusDays(6);

        // 4. 한국 시간 기준 주간 범위를 UTC로 변환
        ZoneOffset kst = ZoneOffset.ofHours(9);

        LocalDateTime start = weekStartDate
                .atStartOfDay()
                .atOffset(kst)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // 다음 주 월요일 00:00은 조회에서 제외
        LocalDateTime end = weekStartDate
                .plusDays(7)
                .atStartOfDay()
                .atOffset(kst)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // 5. 시연 기록을 제외한 해당 주 활동 조회
        List<Activity> activities = activityRepository
                .findByMemberIdAndSimulatedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
                        memberId,
                        start,
                        end
                );

        // 6. 일별 통계 초기화 (월요일 ~ 일요일 7일)
        Map<LocalDate, DailyAccumulator> dailyMap = new LinkedHashMap<>();

        for (int i = 0; i < 7; i++) {

            LocalDate currentDate = weekStartDate.plusDays(i);

            dailyMap.put(
                    currentDate,
                    new DailyAccumulator()
            );
        }

        // 7. 주간 전체 통계 계산
        long activityCount = 0;
        long elapsedSeconds = 0;

        BigDecimal distanceMeters = BigDecimal.ZERO;

        for (Activity activity : activities) {

            // DB UTC -> 한국 날짜
            LocalDate activityDate = activity.getStartedAt()
                    .atOffset(ZoneOffset.UTC)
                    .withOffsetSameInstant(kst)
                    .toLocalDate();

            DailyAccumulator daily = dailyMap.get(activityDate);

            // 해당 날짜의 통계
            if (daily != null) {

                daily.activityCount++;

                daily.distanceMeters = daily.distanceMeters.add(
                        activity.getDistanceMeters()
                );

                daily.elapsedSeconds += activity.getElapsedSeconds();
            }

            // 주간 합계
            activityCount++;

            distanceMeters = distanceMeters.add(
                    activity.getDistanceMeters()
            );

            elapsedSeconds += activity.getElapsedSeconds();
        }

        // 8. 일별 통계 DTO 변환
        List<ActivityWeeklyStatsResponse.Daily> dailyResponse =
                dailyMap.entrySet()
                        .stream()
                        .map(entry ->
                                ActivityWeeklyStatsResponse.Daily.builder()
                                        .date(entry.getKey())
                                        .activityCount(entry.getValue().activityCount)
                                        .distanceMeters(entry.getValue().distanceMeters)
                                        .elapsedSeconds(entry.getValue().elapsedSeconds)
                                        .build()
                        )
                        .toList();

        // 9. 최근 활동 3건 생성
        List<ActivityWeeklyStatsResponse.RecentActivity> recentActivities =
                activities.stream()
                        .limit(3)
                        .map(activity -> {

                            String routeName = routeRepository
                                    .findById(activity.getRouteId())
                                    .map(route -> route.getName())
                                    .orElseThrow(() ->
                                            new CustomException(ErrorCode.ROUTE_NOT_FOUND)
                                    );

                            OffsetDateTime startedAt = activity.getStartedAt()
                                    .atOffset(ZoneOffset.UTC)
                                    .withOffsetSameInstant(kst);

                            return ActivityWeeklyStatsResponse.RecentActivity.builder()
                                    .activityId(activity.getId())
                                    .routeName(routeName)
                                    .activityType(activity.getActivityType())
                                    .distanceMeters(activity.getDistanceMeters())
                                    .startedAt(startedAt)
                                    .build();
                        })
                        .toList();

        // 10. 최종 응답 반환
        return ActivityWeeklyStatsResponse.builder()
                .weekStartDate(weekStartDate)
                .weekEndDate(weekEndDate)
                .activityCount(activityCount)
                .distanceMeters(distanceMeters)
                .elapsedSeconds(elapsedSeconds)
                .daily(dailyResponse)
                .recentActivities(recentActivities)
                .build();
    }

    /**
     * 일별 통계 누적용 클래스
     */
    private static class DailyAccumulator {

        private long activityCount = 0;

        private BigDecimal distanceMeters = BigDecimal.ZERO;

        private long elapsedSeconds = 0;
    }
    /**
     * ACT-05 월간 활동 통계 조회
     */
    public ActivityMonthlyStatsResponse getMonthlyStats(
            Long memberId,
            String month
    ) {

        // 1. 회원 확인
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        // 2. 조회 월 설정
        YearMonth yearMonth;

        try {
            yearMonth = (month == null || month.isBlank())
                    ? YearMonth.now(ZoneId.of("Asia/Seoul"))
                    : YearMonth.parse(month);

        } catch (DateTimeException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 3. 한국 시간 기준 월 시작과 다음 달 시작
        ZoneOffset kst = ZoneOffset.ofHours(9);

        LocalDateTime start = yearMonth
                .atDay(1)
                .atStartOfDay()
                .atOffset(kst)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime end = yearMonth
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay()
                .atOffset(kst)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        // 4. 시연 기록을 제외한 월간 활동 조회
        List<Activity> activities = activityRepository
                .findByMemberIdAndSimulatedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        memberId,
                        start,
                        end
                );

        // 5. 월간 합계 계산
        long activityCount = activities.size();

        long elapsedSeconds = 0;

        BigDecimal distanceMeters = BigDecimal.ZERO;

        // 활동 날짜 중복 제거 및 정렬
        Set<LocalDate> activityDateSet = new TreeSet<>();

        for (Activity activity : activities) {

            distanceMeters = distanceMeters.add(
                    activity.getDistanceMeters()
            );

            elapsedSeconds += activity.getElapsedSeconds();

            // UTC -> 한국 날짜
            LocalDate activityDate = activity.getStartedAt()
                    .atOffset(ZoneOffset.UTC)
                    .withOffsetSameInstant(kst)
                    .toLocalDate();

            activityDateSet.add(activityDate);
        }

        // 6. 활동 날짜 목록
        List<LocalDate> activityDates = new ArrayList<>(activityDateSet);

        // 7. 최장 연속 활동 일수 계산
        long longestStreakDays = 0;
        long streak = 0;

        LocalDate previousDate = null;

        for (LocalDate currentDate : activityDates) {

            if (previousDate != null &&
                    previousDate.plusDays(1).equals(currentDate)) {

                streak++;

            } else {

                streak = 1;
            }

            longestStreakDays = Math.max(
                    longestStreakDays,
                    streak
            );

            previousDate = currentDate;
        }

        // 8. 현재 연속 활동 일수 계산
        // 현재 월은 오늘까지, 과거 월은 해당 월 마지막 날까지 계산
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        LocalDate streakEndDate = today.isBefore(yearMonth.atEndOfMonth())
                ? today
                : yearMonth.atEndOfMonth();

        long currentStreakDays = 0;

        LocalDate checkDate = streakEndDate;

        // 진행 중인 오늘은 아직 기록이 없어도 연속이 끊긴 것으로 보지 않는다.
        // 오늘 기록이 없으면 어제부터 거꾸로 센다.
        if (streakEndDate.equals(today) && !activityDateSet.contains(checkDate)) {
            checkDate = checkDate.minusDays(1);
        }

        while (activityDateSet.contains(checkDate)) {

            currentStreakDays++;

            checkDate = checkDate.minusDays(1);
        }

        // 9. 최종 응답
        return ActivityMonthlyStatsResponse.builder()
                .month(yearMonth.toString())
                .activityCount(activityCount)
                .distanceMeters(distanceMeters)
                .elapsedSeconds(elapsedSeconds)
                .activityDates(activityDates)
                .currentStreakDays(currentStreakDays)
                .longestStreakDays(longestStreakDays)
                .build();
    }
}