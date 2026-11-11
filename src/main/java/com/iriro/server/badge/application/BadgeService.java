package com.iriro.server.badge.application;

import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.domain.BadgeAward;
import com.iriro.server.badge.domain.BadgeCode;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.badge.presentation.BadgeElementResponse;
import com.iriro.server.badge.presentation.BadgeListResponse;
import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeService {

    private final MemberRepository memberRepository;
    private final ActivityRepository activityRepository;
    private final BadgeAwardRepository badgeAwardRepository;

    /**
     * 회원 배지 획득 현황 목록 조회
     */
    public BadgeListResponse getMyBadges(Long memberId) {
        // 1. 회원 존재 여부 및 null 검증 (BADGE-01-F01 대응)
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        // 2. 회원의 현재 활동 통계 조회 (활동 기록이 0개일 때 SUM NULL 처리 포함)
        long totalActivityCount = activityRepository.countByMemberId(memberId);
        Long totalDistanceMetersNullSafe = activityRepository.sumDistanceByMemberId(memberId);
        long totalDistanceMeters = Optional.ofNullable(totalDistanceMetersNullSafe).orElse(0L);

        // 3. 회원의 배지 획득 내역 Map 변환 (BadgeCode -> earnedAt)
        Map<BadgeCode, LocalDateTime> awardMap = badgeAwardRepository.findByMemberId(memberId)
                .stream()
                .collect(Collectors.toMap(BadgeAward::getBadgeCode, BadgeAward::getEarnedAt));

        // 4. 전체 정의된 배지 항목별 진행도(progress) 및 획득 시각 계산 (BADGE-01-S01, BADGE-01-E02 대응)
        List<BadgeElementResponse> badgeResponses = Arrays.stream(BadgeCode.values())
                .map(badgeCode -> {
                    long progress = calculateProgress(badgeCode, totalActivityCount, totalDistanceMeters);
                    LocalDateTime earnedAt = awardMap.get(badgeCode); // 미획득 시 null 반환
                    return BadgeElementResponse.of(badgeCode, progress, earnedAt);
                })
                .toList();

        // 5. 획득한 배지 수 계산 및 최종 응답 DTO 생성
        long earnedCount = awardMap.size();
        return BadgeListResponse.of(earnedCount, badgeResponses);
    }

    /**
     * 배지 종류별 현재 진척도(progress) 계산
     */
    private long calculateProgress(BadgeCode badgeCode, long totalActivityCount, long totalDistanceMeters) {
        return switch (badgeCode) {
            case FIRST_ACTIVITY, FIVE_ACTIVITIES -> totalActivityCount;
            case TEN_KM -> totalDistanceMeters;
        };
    }
}