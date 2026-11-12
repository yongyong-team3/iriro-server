package com.iriro.server.activity.application;

import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.activity.presentation.*;
import com.iriro.server.badge.application.BadgeService;
import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.favorite.domain.Favorite;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.infrastructure.RouteRepository;
import com.iriro.server.trail.infrastructure.RouteStartPointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * 충돌 해결본
 *  - ACT-01 저장(save): dev 구현 그대로 (회원 행 잠금으로 같은 키 재시도 직렬화, 배지 지급, 시연 허용 설정)
 *  - ACT-02~05 조회: 기존 구현 유지
 *
 * dev 코드의 필드 이름(activities, members, ...)은 조회 메서드의 지역 변수(activities)와 겹쳐서
 * 조회 메서드가 쓰던 이름(activityRepository, memberRepository, ...)으로 통일했다.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {

    private static final Set<String> ACTIVITY_TYPES =
            Set.of("WALK", "RUN", "BIKE");

    private final ActivityRepository activityRepository;
    private final MemberRepository memberRepository;
    private final RouteRepository routeRepository;
    private final RouteStartPointRepository startPointRepository;
    private final FavoriteRepository favoriteRepository;
    private final BadgeService badgeService;
    private final ObjectMapper objectMapper;

    @Value("${iriro.activity.allow-simulation:false}")
    private boolean allowSimulation;

    public ActivityService(ActivityRepository activityRepository, MemberRepository memberRepository,
                           RouteRepository routeRepository, RouteStartPointRepository startPointRepository,
                           FavoriteRepository favoriteRepository, BadgeService badgeService,
                           ObjectMapper objectMapper) {
        this.activityRepository = activityRepository;
        this.memberRepository = memberRepository;
        this.routeRepository = routeRepository;
        this.startPointRepository = startPointRepository;
        this.favoriteRepository = favoriteRepository;
        this.badgeService = badgeService;
        this.objectMapper = objectMapper;
    }

    // =====================================================================
    // ACT-01 활동 기록 저장 (dev)
    // =====================================================================

    @Transactional
    public ActivitySaveResponse save(Long memberId, String key, ActivitySaveRequest request) {
        validateKeyAndTime(key, request);
        if (request.isSimulated() && !allowSimulation) throw invalid();
        // 회원 단위 잠금으로 같은 키 재시도와 배지 중복 지급을 직렬화한다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보를 확인해 주세요."));
        String hash = requestHash(request);
        Activity existing = activityRepository.findByMemberIdAndIdempotencyKey(memberId, key).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "이미 처리한 요청과 내용이 다릅니다.");
            }
            return new ActivitySaveResponse(existing.getId(), existing.isFavoriteAdded(), existing.isSimulated());
        }

        Route route = routeRepository.findById(request.routeId()).orElseThrow(this::notFound);
        if (!startPointRepository.existsByIdAndRouteId(request.startPointId(), request.routeId())) throw notFound();
        if (!route.getActivityTypes().contains(request.activityType())) throw invalid();

        boolean favoriteAdded = false;
        if (request.shouldSaveFavorite()
                && favoriteRepository.findByMemberIdAndRouteId(memberId, route.getId()).isEmpty()) {
            favoriteRepository.save(new Favorite(member, route));
            favoriteAdded = true;
        }

        Activity activity = activityRepository.saveAndFlush(Activity.builder()
                .member(member).routeId(route.getId()).startPointId(request.startPointId())
                .activityType(request.activityType())
                .startedAt(utc(request.startedAt())).endedAt(utc(request.endedAt()))
                .elapsedSeconds(request.elapsedSeconds()).distanceMeters(request.distanceMeters())
                .trackPointsJson(trackJson(request)).simulated(request.isSimulated())
                .favoriteAdded(favoriteAdded).idempotencyKey(key).requestHash(hash).build());
        if (!activity.isSimulated()) badgeService.awardEarnedBadges(member);
        return new ActivitySaveResponse(activity.getId(), favoriteAdded, activity.isSimulated());
    }

    private void validateKeyAndTime(String key, ActivitySaveRequest request) {
        try {
            UUID.fromString(key);
        } catch (RuntimeException exception) {
            throw invalid();
        }
        if (request.startedAt() == null || request.endedAt() == null
                || !request.endedAt().toInstant().isAfter(request.startedAt().toInstant())) throw invalid();
    }

    private String requestHash(ActivitySaveRequest request) {
        try {
            ObjectNode normalized = objectMapper.valueToTree(request);
            normalized.put("simulated", request.isSimulated());
            normalized.put("saveAsFavorite", request.shouldSaveFavorite());
            byte[] bytes = objectMapper.writeValueAsBytes(normalized);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (RuntimeException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash activity request", exception);
        }
    }

    private String trackJson(ActivitySaveRequest request) {
        try {
            return objectMapper.writeValueAsString(request.trackPoints());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot serialize track points", exception);
        }
    }

    private LocalDateTime utc(java.time.OffsetDateTime time) {
        return LocalDateTime.ofInstant(time.toInstant(), ZoneOffset.UTC);
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "대상을 찾을 수 없습니다.");
    }

    // =====================================================================
    // ACT-02 활동 기록 목록 조회
    // =====================================================================

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

    // =====================================================================
    // ACT-03 활동 기록 상세 조회
    // =====================================================================

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

    // =====================================================================
    // ACT-04 주간 활동 통계 조회
    // =====================================================================

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

    // =====================================================================
    // ACT-05 월간 활동 통계 조회
    // =====================================================================

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