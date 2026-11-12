package com.iriro.server.activity.application;

import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.trail.infrastructure.RouteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

class ActivityQueryServiceTest {
    private final ActivityRepository activities = mock(ActivityRepository.class);
    private final RouteRepository routes = mock(RouteRepository.class);
    private final ActivityQueryService service = new ActivityQueryService(activities, routes, new ObjectMapper(), false);

    @Test
    void productionListExcludesSimulatedRecords() {
        given(activities.findMonthPage(eq(1L), any(), any(), isNull(), eq(false), any(Pageable.class)))
                .willReturn(Page.empty());

        assertThat(service.list(1L, "2026-11", null, 0, 20).content()).isEmpty();

        then(activities).should().findMonthPage(eq(1L), any(), any(), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void productionDetailHidesExistingSimulation() {
        given(activities.findByIdAndMemberId(501L, 1L))
                .willReturn(Optional.of(Activity.builder().simulated(true).build()));

        assertThatThrownBy(() -> service.detail(1L, 501L))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("ACTIVITY_NOT_FOUND"));
        then(routes).shouldHaveNoInteractions();
    }
}
