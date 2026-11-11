package com.iriro.server.activity.application;

import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.activity.presentation.ActivityDetailResponse;
import com.iriro.server.activity.presentation.ActivityListResponse;
import com.iriro.server.activity.presentation.WeeklyActivityStatsResponse;
import com.iriro.server.activity.presentation.MonthlyActivityStatsResponse;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.infrastructure.RouteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ActivityQueryService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Set<String> TYPES = Set.of("WALK", "RUN", "BIKE");
    private final ActivityRepository activities;
    private final RouteRepository routes;
    private final ObjectMapper mapper;
    private final boolean includeSimulation;

    public ActivityQueryService(ActivityRepository activities, RouteRepository routes, ObjectMapper mapper,
                                @Value("${iriro.activity.allow-simulation:false}") boolean includeSimulation) {
        this.activities = activities;
        this.routes = routes;
        this.mapper = mapper;
        this.includeSimulation = includeSimulation;
    }

    public ActivityListResponse list(Long memberId, String monthText, String activityType, int page, int size) {
        YearMonth month = month(monthText);
        if (page < 0 || size < 1 || size > 100 || (activityType != null && !TYPES.contains(activityType))) {
            throw invalid();
        }
        Page<Activity> found = activities.findMonthPage(memberId, utc(month.atDay(1)),
                utc(month.plusMonths(1).atDay(1)), activityType, includeSimulation,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))));
        Map<Long, String> names = routeNames(found.getContent());
        return new ActivityListResponse(found.getContent().stream()
                .map(a -> new ActivityListResponse.Item(a.getId(), a.getRouteId(), names.get(a.getRouteId()),
                        a.getActivityType(), kst(a.getStartedAt()), a.getDistanceMeters(),
                        a.getElapsedSeconds(), a.isSimulated())).toList(),
                page, size, found.getTotalElements(), found.getTotalPages(), found.hasNext());
    }

    public ActivityDetailResponse detail(Long memberId, Long activityId) {
        Activity a = activities.findByIdAndMemberId(activityId, memberId)
                .orElseThrow(this::notFound);
        if (a.isSimulated() && !includeSimulation) throw notFound();
        String routeName = routes.findById(a.getRouteId()).map(Route::getName).orElseThrow(this::notFound);
        BigDecimal pace = BigDecimal.valueOf(a.getElapsedSeconds()).multiply(BigDecimal.valueOf(1000))
                .divide(a.getDistanceMeters(), 0, RoundingMode.HALF_UP);
        return new ActivityDetailResponse(a.getId(), a.getRouteId(), routeName, a.getActivityType(),
                kst(a.getStartedAt()), kst(a.getEndedAt()), a.getDistanceMeters(), a.getElapsedSeconds(),
                a.isSimulated(), pace.longValue(), geometry(a.getTrackPointsJson()));
    }

    public WeeklyActivityStatsResponse weekly(Long memberId, String dateText) {
        LocalDate date = date(dateText);
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        List<Activity> found = activities
                .findByMemberIdAndSimulatedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDescIdDesc(
                        memberId, utc(monday), utc(monday.plusDays(7)));
        Map<LocalDate, List<Activity>> byDay = found.stream().collect(Collectors.groupingBy(a -> day(a.getStartedAt())));
        List<WeeklyActivityStatsResponse.Daily> daily = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate current = monday.plusDays(i);
            List<Activity> items = byDay.getOrDefault(current, List.of());
            daily.add(new WeeklyActivityStatsResponse.Daily(current, items.size(), distance(items), elapsed(items)));
        }
        List<Activity> latest = found.stream().limit(3).toList();
        Map<Long, String> names = routeNames(latest);
        List<WeeklyActivityStatsResponse.Recent> recent = latest.stream()
                .map(a -> new WeeklyActivityStatsResponse.Recent(a.getId(), names.get(a.getRouteId()),
                        a.getActivityType(), a.getDistanceMeters(), kst(a.getStartedAt()))).toList();
        return new WeeklyActivityStatsResponse(monday, monday.plusDays(6), found.size(),
                distance(found), elapsed(found), daily, recent);
    }

    public MonthlyActivityStatsResponse monthly(Long memberId, String monthText) {
        YearMonth month = month(monthText);
        List<Activity> selected = activities
                .findByMemberIdAndSimulatedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDescIdDesc(
                        memberId, utc(month.atDay(1)), utc(month.plusMonths(1).atDay(1)));
        TreeSet<LocalDate> monthDates = selected.stream().map(a -> day(a.getStartedAt()))
                .collect(Collectors.toCollection(TreeSet::new));
        TreeSet<LocalDate> allDates = activities.findByMemberIdAndSimulatedFalse(memberId).stream()
                .map(a -> day(a.getStartedAt())).collect(Collectors.toCollection(TreeSet::new));
        return new MonthlyActivityStatsResponse(month.toString(), selected.size(), distance(selected), elapsed(selected),
                List.copyOf(monthDates), currentStreak(allDates), longestStreak(allDates));
    }

    private long currentStreak(Set<LocalDate> dates) {
        LocalDate anchor = LocalDate.now(KOREA);
        if (!dates.contains(anchor)) anchor = anchor.minusDays(1);
        long count = 0;
        while (dates.contains(anchor)) {
            count++;
            anchor = anchor.minusDays(1);
        }
        return count;
    }

    private long longestStreak(Set<LocalDate> dates) {
        long longest = 0;
        long run = 0;
        LocalDate previous = null;
        for (LocalDate date : new TreeSet<>(dates)) {
            run = previous != null && date.equals(previous.plusDays(1)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = date;
        }
        return longest;
    }

    YearMonth month(String text) {
        if (text == null) return YearMonth.now(KOREA);
        if (!text.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw invalid();
        try { return YearMonth.parse(text); }
        catch (DateTimeParseException exception) { throw invalid(); }
    }

    LocalDate date(String text) {
        if (text == null) return LocalDate.now(KOREA);
        if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        try { return LocalDate.parse(text); }
        catch (DateTimeParseException exception) { throw invalid(); }
    }

    LocalDateTime utc(LocalDate date) {
        return LocalDateTime.ofInstant(date.atStartOfDay(KOREA).toInstant(), ZoneOffset.UTC);
    }

    LocalDate day(LocalDateTime utc) { return kst(utc).toLocalDate(); }

    OffsetDateTime kst(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toOffsetDateTime();
    }

    BigDecimal distance(List<Activity> found) {
        return found.stream().map(Activity::getDistanceMeters).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    long elapsed(List<Activity> found) {
        return found.stream().mapToLong(Activity::getElapsedSeconds).sum();
    }

    private Map<Long, String> routeNames(List<Activity> found) {
        Set<Long> ids = found.stream().map(Activity::getRouteId).collect(Collectors.toSet());
        return routes.findAllById(ids).stream().collect(Collectors.toMap(Route::getId, Route::getName));
    }

    private ActivityDetailResponse.Geometry geometry(String json) {
        JsonNode points = mapper.readTree(json);
        if (!points.isArray() || points.size() < 2) return null;
        List<List<BigDecimal>> coordinates = new ArrayList<>();
        for (JsonNode point : points) {
            if (!point.path("longitude").isNumber() || !point.path("latitude").isNumber()) return null;
            coordinates.add(List.of(point.path("longitude").decimalValue(), point.path("latitude").decimalValue()));
        }
        return new ActivityDetailResponse.Geometry("LineString", coordinates);
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACTIVITY_NOT_FOUND", "활동 기록을 찾을 수 없습니다.");
    }
}
