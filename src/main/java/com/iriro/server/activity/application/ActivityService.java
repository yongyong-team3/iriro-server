package com.iriro.server.activity.application;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.activity.presentation.ActivitySaveRequest;
import com.iriro.server.activity.presentation.ActivitySaveResponse;
import com.iriro.server.badge.application.BadgeService;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.favorite.domain.Favorite;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.infrastructure.RouteRepository;
import com.iriro.server.trail.infrastructure.RouteStartPointRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ActivityService {
    private final ActivityRepository activities;
    private final MemberRepository members;
    private final RouteRepository routes;
    private final RouteStartPointRepository points;
    private final FavoriteRepository favorites;
    private final BadgeService badges;
    private final ObjectMapper mapper;

    @Value("${iriro.activity.allow-simulation:false}")
    private boolean allowSimulation;

    public ActivityService(ActivityRepository activities, MemberRepository members, RouteRepository routes,
                           RouteStartPointRepository points, FavoriteRepository favorites, BadgeService badges,
                           ObjectMapper mapper) {
        this.activities = activities;
        this.members = members;
        this.routes = routes;
        this.points = points;
        this.favorites = favorites;
        this.badges = badges;
        this.mapper = mapper;
    }

    @Transactional
    public ActivitySaveResponse save(Long memberId, String key, ActivitySaveRequest request) {
        validateKeyAndTime(key, request);
        if (request.isSimulated() && !allowSimulation) throw invalid();
        // 회원 단위 잠금으로 같은 키 재시도와 배지 중복 지급을 직렬화한다.
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보를 확인해 주세요."));
        String hash = requestHash(request);
        Activity existing = activities.findByMemberIdAndIdempotencyKey(memberId, key).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "이미 처리한 요청과 내용이 다릅니다.");
            }
            return new ActivitySaveResponse(existing.getId(), existing.isFavoriteAdded(), existing.isSimulated());
        }

        Route route = routes.findById(request.routeId()).orElseThrow(this::notFound);
        if (!points.existsByIdAndRouteId(request.startPointId(), request.routeId())) throw notFound();
        if (!route.getActivityTypes().contains(request.activityType())) throw invalid();

        boolean favoriteAdded = false;
        if (request.shouldSaveFavorite() && favorites.findByMemberIdAndRouteId(memberId, route.getId()).isEmpty()) {
            favorites.save(new Favorite(member, route));
            favoriteAdded = true;
        }

        Activity activity = activities.saveAndFlush(Activity.builder()
                .member(member).routeId(route.getId()).startPointId(request.startPointId())
                .activityType(request.activityType())
                .startedAt(utc(request.startedAt())).endedAt(utc(request.endedAt()))
                .elapsedSeconds(request.elapsedSeconds()).distanceMeters(request.distanceMeters())
                .trackPointsJson(trackJson(request)).simulated(request.isSimulated())
                .favoriteAdded(favoriteAdded).idempotencyKey(key).requestHash(hash).build());
        if (!activity.isSimulated()) badges.awardEarnedBadges(member);
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
            ObjectNode normalized = mapper.valueToTree(request);
            normalized.put("simulated", request.isSimulated());
            normalized.put("saveAsFavorite", request.shouldSaveFavorite());
            byte[] bytes = mapper.writeValueAsBytes(normalized);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (RuntimeException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash activity request", exception);
        }
    }

    private String trackJson(ActivitySaveRequest request) {
        try {
            return mapper.writeValueAsString(request.trackPoints());
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
}
