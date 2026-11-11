package com.iriro.server.member.application;

import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.auth.domain.EmailCredential;
import com.iriro.server.auth.infrastructure.EmailCredentialRepository;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import java.util.Locale;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import com.iriro.server.member.presentation.MyInfoResponse;
import com.iriro.server.member.presentation.NicknameCheckResponse;
import com.iriro.server.member.presentation.UpdateProfileRequest;
import com.iriro.server.member.presentation.UpdateProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final MemberRepository memberRepository;
    private final ActivityRepository activityRepository;
    private final BadgeAwardRepository badgeAwardRepository;
    private final EmailCredentialRepository emailCredentialRepository;
    private final NotificationRepository notificationRepository;

    /**
     * 내 프로필 정보 및 활동 요약 조회
     */
    public MyInfoResponse getMyInfo(Long memberId) {
        // 1. 회원 정보 조회 (존재하지 않을 경우 401/404 처리)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED_USER));
        String email = emailCredentialRepository.findByMemberId(memberId)
                .map(EmailCredential::getEmailNormalized)
                .orElse(null);

        // 2. 활동 및 지표 집계 데이터 조회 (집계 데이터가 없으면 기본값 0 처리)
        long totalActivityCount = activityRepository.countByMemberIdAndSimulatedFalse(memberId);
        long totalDistanceMeters = activityRepository.sumRealDistanceByMemberId(memberId);
        long earnedBadgeCount = badgeAwardRepository.countByMemberId(memberId);
        long unreadNotificationCount = notificationRepository.countByMember_IdAndReadAtIsNull(memberId);

        // 3. 응답 DTO 생성 및 반환
        return MyInfoResponse.of(
                member,
                email,
                totalActivityCount,
                totalDistanceMeters,
                earnedBadgeCount,
                unreadNotificationCount
        );
    }

    /**
     * 내 프로필 정보 수정 (닉네임 변경)
     */
    @Transactional
    public UpdateProfileResponse updateProfile(Long memberId, UpdateProfileRequest request) {
        // 1. 회원 존재 여부 확인
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED_USER));

        String newMemberName = request.getMemberName();
        String newMemberNameKey = newMemberName.toLowerCase(Locale.ROOT);

        // 2. 닉네임 중복 재검사 (본인 제외 타 회원이 동일 키를 사용 중인 경우 409 DUPLICATE_MEMBER_NAME)
        if (memberRepository.existsByMemberNameKeyAndIdNot(newMemberNameKey, memberId)) {
            throw new CustomException(ErrorCode.DUPLICATE_MEMBER_NAME);
        }

        // 3. 엔티티 수정 (JPA Dirty Checking으로 자동 update)
        member.updateMemberName(newMemberName);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.DUPLICATE_MEMBER_NAME);
        }

        // 4. 응답 DTO 변환 및 반환
        return UpdateProfileResponse.from(member);
    }

    /**
     * 닉네임 사용 가능 여부 확인
     */
    public NicknameCheckResponse checkNicknameAvailability(String memberName) {
        // 영문 대소문자 무시 동일 비교를 위해 소문자로 정규화
        String memberNameKey = memberName.toLowerCase(Locale.ROOT);

        // DB에 존재하지 않으면 사용 가능(true), 존재하면 사용 불가(false)
        boolean isAvailable = !memberRepository.existsByMemberNameKey(memberNameKey);

        return NicknameCheckResponse.of(memberName, isAvailable);
    }
}
