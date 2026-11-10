package com.iriro.server.member.application;

import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.common.CustomException;
import com.iriro.server.member.domain.EmailCredential;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.EmailCredentialRepository;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.member.presentation.MyInfoResponse;
import com.iriro.server.member.presentation.NicknameCheckResponse;
import com.iriro.server.member.presentation.UpdateProfileRequest;
import com.iriro.server.member.presentation.UpdateProfileResponse;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock ActivityRepository activityRepository;
    @Mock BadgeAwardRepository badgeAwardRepository;
    @Mock EmailCredentialRepository emailCredentialRepository;
    @Mock NotificationRepository notificationRepository;

    @InjectMocks
    UserService userService;

    /** id는 DB가 채워주는 값이라 테스트에서는 리플렉션으로 넣어준다. */
    private Member createMember(Long id, String memberName) {
        Member member = new Member(memberName, memberName.toLowerCase());
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Nested
    @DisplayName("내 정보 조회 getMyInfo")
    class GetMyInfo {

        @Test
        @DisplayName("회원, 이메일, 활동/뱃지/알림 집계가 응답에 담긴다")
        void 성공() {
            // given
            Member member = createMember(1L, "Runner");
            EmailCredential credential = mock(EmailCredential.class);
            given(credential.getEmailNormalized()).willReturn("runner@test.com");

            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(emailCredentialRepository.findByMemberId(1L)).willReturn(Optional.of(credential));
            given(activityRepository.countByMemberId(1L)).willReturn(10L);
            given(activityRepository.sumDistanceByMemberId(1L)).willReturn(42_000L);
            given(badgeAwardRepository.countByMemberId(1L)).willReturn(3L);
            given(notificationRepository.countByMemberIdAndIsReadFalse(1L)).willReturn(2L);

            // when
            MyInfoResponse result = userService.getMyInfo(1L);

            // then
            assertThat(result.getMemberId()).isEqualTo(1L);
            assertThat(result.getMemberName()).isEqualTo("Runner");
            assertThat(result.getEmail()).isEqualTo("runner@test.com");
            assertThat(result.getTotalActivityCount()).isEqualTo(10L);
            assertThat(result.getTotalDistanceMeters()).isEqualTo(42_000L);
            assertThat(result.getEarnedBadgeCount()).isEqualTo(3L);
            assertThat(result.getUnreadNotificationCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("이메일 정보가 없으면 email은 null이고 나머지는 정상 조회된다")
        void 이메일_없으면_null() {
            // given
            Member member = createMember(1L, "Runner");
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(emailCredentialRepository.findByMemberId(1L)).willReturn(Optional.empty());
            given(activityRepository.countByMemberId(1L)).willReturn(0L);
            given(activityRepository.sumDistanceByMemberId(1L)).willReturn(0L);
            given(badgeAwardRepository.countByMemberId(1L)).willReturn(0L);
            given(notificationRepository.countByMemberIdAndIsReadFalse(1L)).willReturn(0L);

            // when
            MyInfoResponse result = userService.getMyInfo(1L);

            // then
            assertThat(result.getEmail()).isNull();
            assertThat(result.getMemberName()).isEqualTo("Runner");
            assertThat(result.getTotalActivityCount()).isZero();
            assertThat(result.getEarnedBadgeCount()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 예외가 발생하고 집계 조회는 하지 않는다")
        void 회원_없으면_예외() {
            // given
            given(memberRepository.findById(99L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.getMyInfo(99L))
                    .isInstanceOf(CustomException.class);
            // 에러코드까지 검증하려면 CustomException의 필드명에 맞춰 아래를 추가:
            // .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_USER);

            verifyNoInteractions(activityRepository, badgeAwardRepository, notificationRepository);
        }
    }

    @Nested
    @DisplayName("프로필 수정 updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임이 변경되고, 중복 검사에는 소문자 키와 본인 id가 사용된다")
        void 성공() {
            // given
            Member member = createMember(1L, "OldName");
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(memberRepository.existsByMemberNameKeyAndIdNot("newname", 1L)).willReturn(false);

            // when
            UpdateProfileResponse result = userService.updateProfile(1L, new UpdateProfileRequest("NewName"));

            // then
            assertThat(result.getMemberId()).isEqualTo(1L);
            assertThat(result.getMemberName()).isEqualTo("NewName");
            assertThat(member.getMemberName()).isEqualTo("NewName");
            assertThat(member.getMemberNameKey()).isEqualTo("newname");
            verify(memberRepository).existsByMemberNameKeyAndIdNot("newname", 1L);
        }

        @Test
        @DisplayName("다른 회원이 같은 닉네임(대소문자 무시)을 쓰고 있으면 예외가 나고 닉네임은 그대로다")
        void 닉네임_중복이면_예외() {
            // given
            Member member = createMember(1L, "OldName");
            given(memberRepository.findById(1L)).willReturn(Optional.of(member));
            given(memberRepository.existsByMemberNameKeyAndIdNot("taken", 1L)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.updateProfile(1L, new UpdateProfileRequest("TAKEN")))
                    .isInstanceOf(CustomException.class);
            // .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_MEMBER_NAME);

            assertThat(member.getMemberName()).isEqualTo("OldName");
            assertThat(member.getMemberNameKey()).isEqualTo("oldname");
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 예외가 발생하고 중복 검사는 하지 않는다")
        void 회원_없으면_예외() {
            // given
            given(memberRepository.findById(99L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.updateProfile(99L, new UpdateProfileRequest("NewName")))
                    .isInstanceOf(CustomException.class);
            // .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED_USER);

            verify(memberRepository).findById(99L);
            verifyNoMoreInteractionsWithDuplicateCheck();
        }

        private void verifyNoMoreInteractionsWithDuplicateCheck() {
            org.mockito.Mockito.verify(memberRepository, org.mockito.Mockito.never())
                    .existsByMemberNameKeyAndIdNot(org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyLong());
        }
    }

    @Nested
    @DisplayName("닉네임 사용 가능 여부 checkNicknameAvailability")
    class CheckNicknameAvailability {

        @Test
        @DisplayName("DB에 없으면 사용 가능하고, 조회는 소문자 키로 하되 응답엔 입력값 그대로 담긴다")
        void 사용_가능() {
            // given
            given(memberRepository.existsByMemberNameKey("hello")).willReturn(false);

            // when
            NicknameCheckResponse result = userService.checkNicknameAvailability("Hello");

            // then
            assertThat(result.getMemberName()).isEqualTo("Hello");
            assertThat(result.isAvailable()).isTrue();
            verify(memberRepository).existsByMemberNameKey("hello");
        }

        @Test
        @DisplayName("이미 있으면 사용 불가")
        void 사용_불가() {
            // given
            given(memberRepository.existsByMemberNameKey("hello")).willReturn(true);

            // when
            NicknameCheckResponse result = userService.checkNicknameAvailability("HELLO");

            // then
            assertThat(result.getMemberName()).isEqualTo("HELLO");
            assertThat(result.isAvailable()).isFalse();
        }
    }
}