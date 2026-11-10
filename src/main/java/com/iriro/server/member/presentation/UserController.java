package com.iriro.server.member.presentation;

import com.iriro.server.common.ApiResponse;
import com.iriro.server.member.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User API", description = "회원 관련 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "내 프로필과 활동 요약 정보를 조회합니다. (임시: 인증 없음)")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(
            // Security 인증 적용 전 임시 처리:
            // 파라미터를 넘기지 않으면 기본 1L 회원을 조회하고, 필요 시 ?memberId=2 형태로 변경하여 테스트 가능합니다.
            @RequestParam(value = "memberId", defaultValue = "1") Long currentMemberId
    ) {
        MyInfoResponse responseData = userService.getMyInfo(currentMemberId);
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", responseData));
    }

    @Operation(summary = "프로필 수정", description = "내 닉네임을 변경합니다. (임시: 인증 없음)")
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestParam(value = "memberId", defaultValue = "1") Long currentMemberId
    ) {
        UpdateProfileResponse responseData = userService.updateProfile(currentMemberId, request);
        return ResponseEntity.ok(ApiResponse.success("프로필이 수정되었습니다.", responseData));
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임 사용 가능 여부를 확인합니다. (인증 불필요)")
    @GetMapping("/nickname-availability")
    public ResponseEntity<ApiResponse<NicknameCheckResponse>> checkNicknameAvailability(
            @Valid @ModelAttribute NicknameCheckRequest request
    ) {
        NicknameCheckResponse responseData = userService.checkNicknameAvailability(request.getMemberName());
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", responseData));
    }
}