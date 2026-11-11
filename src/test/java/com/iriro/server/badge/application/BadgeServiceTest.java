package com.iriro.server.badge.application;

import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.domain.BadgeAward;
import com.iriro.server.badge.domain.BadgeCode;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.badge.presentation.BadgeElementResponse;
import com.iriro.server.badge.presentation.BadgeListResponse;
import com.iriro.server.common.CustomException;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {
    @Mock MemberRepository members;
    @Mock ActivityRepository activities;
    @Mock BadgeAwardRepository awards;
    @InjectMocks BadgeService service;

    @Test
    void eightReplacementBadgesAndComputableProgress() {
        given(members.existsById(1L)).willReturn(true);
        given(activities.findByMemberIdAndSimulatedFalse(1L)).willReturn(List.of(
                activity(11L, "WALK", 1500, 1),
                activity(11L, "WALK", 1500, 2),
                activity(11L, "WALK", 1500, 3),
                activity(12L, "BIKE", 5000, 4)));
        given(awards.findByMemberId(1L)).willReturn(List.of());

        BadgeListResponse response = service.getMyBadges(1L);

        assertThat(response.getTotalCount()).isEqualTo(8);
        assertThat(response.getBadges()).extracting(BadgeElementResponse::getCode)
                .doesNotContain("FIRST_ACTIVITY", "FIVE_ACTIVITIES", "TEN_KM");
        assertThat(badge(response, BadgeCode.WALK_LOVER).getProgress()).isEqualTo(3);
        assertThat(badge(response, BadgeCode.YONGIN_CITY_EXPLORER).getProgress()).isEqualTo(1);
        assertThat(badge(response, BadgeCode.BIKE_RIDER).getProgress()).isEqualTo(5000);
        assertThat(badge(response, BadgeCode.CONSISTENT_ACTIVIST).getProgress()).isEqualTo(4);
        assertThat(badge(response, BadgeCode.YONGIN_DRAGON).getProgress()).isEqualTo(9500);
        assertThat(badge(response, BadgeCode.RIVER_BRIDGE).getProgress()).isZero();
        assertThat(response.getBadges()).allSatisfy(b -> assertThat(b.isEarned()).isFalse());
    }

    @Test
    void awardsOnlyAttainableThresholdsOnce() {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        given(activities.findByMemberIdAndSimulatedFalse(1L)).willReturn(List.of(
                activity(11L, "BIKE", 100000, 1), activity(12L, "WALK", 100000, 2)));
        given(awards.findByMemberId(1L)).willReturn(List.of());

        service.awardEarnedBadges(member);

        then(awards).should(times(2)).save(any(BadgeAward.class));
        then(awards).should(never()).save(argThat(a -> a.getBadgeCode() == BadgeCode.RIVER_BRIDGE));
    }

    @Test
    void awardsAllFiveVerifiableBadgesAtTheirThresholdsWithoutDuplicates() {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        List<Activity> records = IntStream.rangeClosed(1, 30)
                .mapToObj(day -> activity(day <= 10 ? 11L : day <= 20 ? 12L : 13L,
                        day <= 20 ? "WALK" : "BIKE", day <= 20 ? 5000 : 10000, day))
                .toList();
        given(activities.findByMemberIdAndSimulatedFalse(1L)).willReturn(records);
        BadgeAward existing = new BadgeAward(member, BadgeCode.WALK_LOVER);
        given(awards.findByMemberId(1L)).willReturn(List.of(existing));

        service.awardEarnedBadges(member);

        ArgumentCaptor<BadgeAward> captured = ArgumentCaptor.forClass(BadgeAward.class);
        then(awards).should(times(4)).save(captured.capture());
        assertThat(captured.getAllValues()).extracting(BadgeAward::getBadgeCode)
                .containsExactlyInAnyOrder(BadgeCode.YONGIN_CITY_EXPLORER, BadgeCode.BIKE_RIDER,
                        BadgeCode.CONSISTENT_ACTIVIST, BadgeCode.YONGIN_DRAGON);
    }

    @Test
    void unknownMemberIsUnauthorized() {
        given(members.existsById(1L)).willReturn(false);
        assertThatThrownBy(() -> service.getMyBadges(1L)).isInstanceOf(CustomException.class);
        then(activities).shouldHaveNoInteractions();
    }

    private BadgeElementResponse badge(BadgeListResponse list, BadgeCode code) {
        return list.getBadges().stream().filter(b -> b.getCode().equals(code.name())).findFirst().orElseThrow();
    }

    private Activity activity(Long routeId, String type, long meters, int day) {
        return Activity.builder().routeId(routeId).activityType(type)
                .distanceMeters(BigDecimal.valueOf(meters))
                .startedAt(LocalDateTime.of(2026, 11, day, 10, 0)).simulated(false).build();
    }
}
