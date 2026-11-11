package com.iriro.server.member.presentation;

import com.iriro.server.common.ApiResponse;
import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.member.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(summary = "내 정보 조회", description = "내 프로필과 활동 요약 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 정보"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")})
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo() {
        MyInfoResponse responseData = userService.getMyInfo(currentMemberProvider.requireMemberId());
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", responseData));
    }

    @Operation(summary = "프로필 수정", description = "내 닉네임을 변경합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "닉네임 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "닉네임 중복")})
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UpdateProfileResponse responseData = userService.updateProfile(currentMemberProvider.requireMemberId(), request);
        return ResponseEntity.ok(ApiResponse.success("프로필이 수정되었습니다.", responseData));
    }

    @Operation(summary = "닉네임 중복 확인", description = "닉네임 사용 가능 여부를 확인합니다. (인증 불필요)")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용 가능 여부"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "닉네임 형식 오류")})
    @GetMapping("/nickname-availability")
    public ResponseEntity<ApiResponse<NicknameCheckResponse>> checkNicknameAvailability(
            @Valid @ModelAttribute NicknameCheckRequest request
    ) {
        NicknameCheckResponse responseData = userService.checkNicknameAvailability(request.getMemberName());
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", responseData));
    }

    @Operation(summary = "회원 탈퇴", description = "계정과 연결된 인증·활동·기기 기록을 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "탈퇴 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")})
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw() {
        userService.withdraw(currentMemberProvider.requireMemberId());
        return ResponseEntity.noContent().build();
    }
}
