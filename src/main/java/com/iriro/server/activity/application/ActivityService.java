package com.iriro.server.activity.application;


import com.iriro.server.activity.presentation.ActivityListResponse;
import com.iriro.server.activity.presentation.ActivityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;
import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.activity.presentation.ActivityCreateRequest;
import com.iriro.server.activity.presentation.ActivityCreateResponse;
import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.favorite.application.FavoriteService;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.trail.infrastructure.RouteRepository;
import com.iriro.server.trail.infrastructure.RouteStartPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
        if (!routeRepository.existsById(request.getRouteId())) {
            throw new CustomException(ErrorCode.ROUTE_NOT_FOUND);
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
}