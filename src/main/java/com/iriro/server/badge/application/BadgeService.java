package com.iriro.server.badge.application;

import com.iriro.server.activity.domain.Activity;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.domain.BadgeAward;
import com.iriro.server.badge.domain.BadgeCode;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.badge.presentation.BadgeElementResponse;
import com.iriro.server.badge.presentation.BadgeListResponse;
import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ActivityRepository activityRepository;
    private final BadgeAwardRepository badgeAwardRepository;

    public BadgeListResponse getMyBadges(Long memberId) {
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }
        Map<BadgeCode, Long> progress = progress(activityRepository.findByMemberIdAndSimulatedFalse(memberId));
        Map<BadgeCode, LocalDateTime> awards = badgeAwardRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(BadgeAward::getBadgeCode, BadgeAward::getEarnedAt));
        List<BadgeElementResponse> badges = Arrays.stream(BadgeCode.values())
                .map(code -> BadgeElementResponse.of(code, progress.get(code), awards.get(code)))
                .toList();
        return BadgeListResponse.of(awards.size(), badges);
    }

    /** 활동 저장 트랜잭션 안에서 호출해 신규 지급을 기록한다. */
    @Transactional
    public void awardEarnedBadges(Member member) {
        Map<BadgeCode, Long> progress = progress(activityRepository.findByMemberIdAndSimulatedFalse(member.getId()));
        Set<BadgeCode> earned = badgeAwardRepository.findByMemberId(member.getId()).stream()
                .map(BadgeAward::getBadgeCode).collect(Collectors.toSet());
        for (BadgeCode code : BadgeCode.values()) {
            if (isVerifiable(code) && !earned.contains(code) && progress.get(code) >= code.getTarget()) {
                badgeAwardRepository.save(new BadgeAward(member, code));
            }
        }
    }

    private boolean isVerifiable(BadgeCode code) {
        return switch (code) {
            // 지정 코스·다리·하천 산책로 분류가 데이터에 없어 아직 판정할 수 없다.
            case COURSE_FINISHER, RIVER_BRIDGE, RIVER_WALKWAY -> false;
            default -> true;
        };
    }

    private Map<BadgeCode, Long> progress(List<Activity> activities) {
        Map<BadgeCode, Long> result = new EnumMap<>(BadgeCode.class);
        for (BadgeCode code : BadgeCode.values()) result.put(code, 0L);
        long walkCount = activities.stream().filter(a -> "WALK".equals(a.getActivityType())).count();
        long bikeMeters = sumMeters(activities.stream().filter(a -> "BIKE".equals(a.getActivityType())).toList());
        long allMeters = sumMeters(activities);
        long repeatRoutes = activities.stream().collect(Collectors.groupingBy(Activity::getRouteId, Collectors.counting()))
                .values().stream().filter(count -> count >= 3).count();
        long distinctDays = activities.stream().map(Activity::getStartedAt)
                .map(start -> start.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toLocalDate())
                .distinct().count();
        result.put(BadgeCode.WALK_LOVER, walkCount);
        result.put(BadgeCode.YONGIN_CITY_EXPLORER, repeatRoutes);
        result.put(BadgeCode.BIKE_RIDER, bikeMeters);
        result.put(BadgeCode.CONSISTENT_ACTIVIST, distinctDays);
        result.put(BadgeCode.YONGIN_DRAGON, allMeters);
        return result;
    }

    private long sumMeters(List<Activity> activities) {
        return activities.stream().map(Activity::getDistanceMeters).reduce(BigDecimal.ZERO, BigDecimal::add).longValue();
    }
}
