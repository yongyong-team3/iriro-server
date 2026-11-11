package com.iriro.server.activity.application;

import tools.jackson.databind.ObjectMapper;
import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.activity.presentation.ActivitySaveRequest;
import com.iriro.server.badge.application.BadgeService;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.infrastructure.RouteRepository;
import com.iriro.server.trail.infrastructure.RouteStartPointRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    @Mock ActivityRepository activities;
    @Mock MemberRepository members;
    @Mock RouteRepository routes;
    @Mock RouteStartPointRepository points;
    @Mock FavoriteRepository favorites;
    @Mock BadgeService badges;
    @Spy ObjectMapper mapper = new ObjectMapper();
    @InjectMocks ActivityService service;

    @Test
    void simulationDoesNotAwardBadges() {
        Member member = mock(Member.class);
        Route route = mock(Route.class);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(routes.findById(11L)).willReturn(Optional.of(route));
        given(route.getActivityTypes()).willReturn(Set.of("WALK"));
        given(points.existsByIdAndRouteId(101L, 11L)).willReturn(true);
        given(activities.saveAndFlush(any(Activity.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.save(1L, UUID.randomUUID().toString(), request(true));

        then(badges).shouldHaveNoInteractions();
    }

    @Test
    void missingRouteReturnsNotFoundBeforeSaving() {
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(mock(Member.class)));
        given(routes.findById(11L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(1L, UUID.randomUUID().toString(), request(false)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("NOT_FOUND"));
        then(activities).should(never()).saveAndFlush(any());
    }

    @Test
    void sameKeyWithChangedSimulationIsConflict() {
        Member member = mock(Member.class);
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(activities.findByMemberIdAndIdempotencyKey(eq(1L), anyString()))
                .willReturn(Optional.of(Activity.builder().simulated(false).requestHash("different").build()));

        assertThatThrownBy(() -> service.save(1L, UUID.randomUUID().toString(), request(true)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        then(badges).shouldHaveNoInteractions();
    }

    @Test
    void identicalRetryReturnsOriginalResultWithoutNewAward() {
        Member member = mock(Member.class);
        Route route = mock(Route.class);
        given(route.getId()).willReturn(11L);
        given(route.getActivityTypes()).willReturn(Set.of("WALK"));
        given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(routes.findById(11L)).willReturn(Optional.of(route));
        given(points.existsByIdAndRouteId(101L, 11L)).willReturn(true);
        java.util.concurrent.atomic.AtomicReference<Activity> stored = new java.util.concurrent.atomic.AtomicReference<>();
        given(activities.findByMemberIdAndIdempotencyKey(eq(1L), anyString()))
                .willAnswer(invocation -> Optional.ofNullable(stored.get()));
        given(activities.saveAndFlush(any(Activity.class))).willAnswer(invocation -> {
            Activity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 501L);
            stored.set(saved);
            return saved;
        });
        String key = UUID.randomUUID().toString();
        service.save(1L, key, request(false));
        ActivitySaveRequest original = request(false);
        ActivitySaveRequest omitted = new ActivitySaveRequest(original.routeId(), original.startPointId(),
                original.activityType(), original.startedAt(), original.endedAt(), original.elapsedSeconds(),
                original.distanceMeters(), original.trackPoints(), original.saveAsFavorite(), null);
        var response = service.save(1L, key, omitted);

        assertThat(response.activityId()).isEqualTo(501L);
        then(activities).should(times(1)).saveAndFlush(any());
        then(badges).should(times(1)).awardEarnedBadges(member);
    }

    private ActivitySaveRequest request(boolean simulated) {
        return new ActivitySaveRequest(11L, 101L, "WALK",
                java.time.OffsetDateTime.parse("2026-11-09T10:00:00+09:00"),
                java.time.OffsetDateTime.parse("2026-11-09T10:30:00+09:00"),
                1800, java.math.BigDecimal.valueOf(2400),
                java.util.List.of(new ActivitySaveRequest.TrackPoint(java.math.BigDecimal.valueOf(37.3), java.math.BigDecimal.valueOf(127.2), java.time.OffsetDateTime.parse("2026-11-09T10:00:00+09:00")),
                        new ActivitySaveRequest.TrackPoint(java.math.BigDecimal.valueOf(37.4), java.math.BigDecimal.valueOf(127.3), java.time.OffsetDateTime.parse("2026-11-09T10:30:00+09:00"))), false,
                simulated);
    }
}
