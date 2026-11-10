package com.iriro.server.badge.application;

import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.domain.BadgeAward;
import com.iriro.server.badge.domain.BadgeCode;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.badge.presentation.BadgeElementResponse;
import com.iriro.server.badge.presentation.BadgeListResponse;
import com.iriro.server.common.CustomException;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private BadgeAwardRepository badgeAwardRepository;

    @InjectMocks
    private BadgeService badgeService;

    private final Long memberId = 1L;

    @BeforeEach
    void setUp() {
        given(memberRepository.existsById(memberId)).willReturn(true);
    }

    @Test
    @DisplayName("배지 목록 조회 성공 - 획득한 배지가 없는 경우")
    void getMyBadges_Success_NoEarnedBadges() {
        // given
        given(activityRepository.countByMemberId(memberId)).willReturn(0L);
        given(activityRepository.sumDistanceByMemberId(memberId)).willReturn(0L);
        given(badgeAwardRepository.findByMemberId(memberId)).willReturn(List.of());

        // when
        BadgeListResponse response = badgeService.getMyBadges(memberId);

        // then
        assertThat(response.getEarnedCount()).isZero();
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getBadges()).hasSize(3);

        assertThat(response.getBadges())
                .extracting(BadgeElementResponse::getProgress)
                .containsExactly(0L, 0L, 0L);

        assertThat(response.getBadges())
                .allSatisfy(badge -> assertThat(badge.getEarnedAt()).isNull());
    }

    @Test
    @DisplayName("배지 목록 조회 성공 - 활동 횟수에 따른 진행도 계산")
    void getMyBadges_Success_ActivityProgress() {
        // given
        given(activityRepository.countByMemberId(memberId)).willReturn(5L);
        given(activityRepository.sumDistanceByMemberId(memberId)).willReturn(3000L);
        given(badgeAwardRepository.findByMemberId(memberId)).willReturn(List.of());

        // when
        BadgeListResponse response = badgeService.getMyBadges(memberId);

        // then
        assertThat(response.getBadges()).hasSize(3);

        BadgeElementResponse firstActivity = response.getBadges().get(0);
        BadgeElementResponse fiveActivities = response.getBadges().get(1);
        BadgeElementResponse tenKm = response.getBadges().get(2);

        assertThat(firstActivity.getCode()).isEqualTo("FIRST_ACTIVITY");
        assertThat(firstActivity.getName()).isEqualTo("첫 활동");
        assertThat(firstActivity.getTarget()).isEqualTo(1L);
        assertThat(firstActivity.getProgress()).isEqualTo(5L);

        assertThat(fiveActivities.getCode()).isEqualTo("FIVE_ACTIVITIES");
        assertThat(fiveActivities.getTarget()).isEqualTo(5L);
        assertThat(fiveActivities.getProgress()).isEqualTo(5L);

        assertThat(tenKm.getCode()).isEqualTo("TEN_KM");
        assertThat(tenKm.getTarget()).isEqualTo(10000L);
        assertThat(tenKm.getProgress()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("배지 목록 조회 성공 - 획득한 배지가 있는 경우")
    void getMyBadges_Success_EarnedBadges() {
        // given
        LocalDateTime earnedAt = LocalDateTime.of(2026, 11, 10, 12, 0);

        BadgeAward firstAward = BadgeAward.builder()
                .member(mock(Member.class))
                .badgeCode(BadgeCode.FIRST_ACTIVITY)
                .build();

        BadgeAward fiveAward = BadgeAward.builder()
                .member(mock(Member.class))
                .badgeCode(BadgeCode.FIVE_ACTIVITIES)
                .build();

        // 실제 획득 시각을 검증하기 위해 리플렉션 대신 아래와 같이
        // 서비스 응답에서 엔티티에 저장된 시각을 사용합니다.
        given(activityRepository.countByMemberId(memberId)).willReturn(5L);
        given(activityRepository.sumDistanceByMemberId(memberId)).willReturn(10000L);
        given(badgeAwardRepository.findByMemberId(memberId))
                .willReturn(List.of(firstAward, fiveAward));

        // when
        BadgeListResponse response = badgeService.getMyBadges(memberId);

        // then
        assertThat(response.getEarnedCount()).isEqualTo(2L);

        assertThat(response.getBadges().get(0).getEarnedAt())
                .isNotNull();

        assertThat(response.getBadges().get(1).getEarnedAt())
                .isNotNull();

        assertThat(response.getBadges().get(2).getEarnedAt())
                .isNull();
    }

    @Test
    @DisplayName("배지 목록 조회 실패 - 존재하지 않는 회원")
    void getMyBadges_Fail_MemberNotFound() {
        // given
        given(memberRepository.existsById(memberId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> badgeService.getMyBadges(memberId))
                .isInstanceOf(CustomException.class);

        then(activityRepository).shouldHaveNoInteractions();
        then(badgeAwardRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배지 목록 조회 성공 - 누적 거리가 목표를 초과한 경우")
    void getMyBadges_Success_DistanceExceedsTarget() {
        // given
        given(activityRepository.countByMemberId(memberId)).willReturn(10L);
        given(activityRepository.sumDistanceByMemberId(memberId)).willReturn(15000L);
        given(badgeAwardRepository.findByMemberId(memberId)).willReturn(List.of());

        // when
        BadgeListResponse response = badgeService.getMyBadges(memberId);

        // then
        BadgeElementResponse tenKm = response.getBadges().get(2);

        assertThat(tenKm.getTarget()).isEqualTo(10000L);
        assertThat(tenKm.getProgress()).isEqualTo(15000L);
    }
}