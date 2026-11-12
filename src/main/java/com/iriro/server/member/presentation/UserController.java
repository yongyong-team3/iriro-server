package com.iriro.server.member.presentation;

import com.iriro.server.common.ApiResponse;
import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.member.application.UserService;
import com.iriro.server.member.application.ProfilePhotoService;
import com.iriro.server.member.domain.MemberProfilePhoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentMemberProvider currentMemberProvider;
    private final ProfilePhotoService profilePhotoService;

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

    @Operation(summary = "프로필 사진 업로드·교체", description = "PNG/JPEG, 최대 2MB·4096px. 응답 URL은 API 서버 기준 상대 경로입니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업로드·교체 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED")})
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfilePhotoResponse>> uploadPhoto(
            @Parameter(required = true, description = "PNG 또는 JPEG 파일", content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(type = "string", format = "binary")))
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        String url = profilePhotoService.upload(currentMemberProvider.requireMemberId(), photo);
        return ResponseEntity.ok(ApiResponse.success("프로필 사진이 변경되었습니다.", new ProfilePhotoResponse(url)));
    }

    @Operation(summary = "프로필 사진 기본값으로 변경", description = "저장된 사진을 삭제합니다. profileImageUrl=null이면 프론트 기본 이미지를 사용합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "기본값으로 변경 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED")})
    @DeleteMapping("/me/photo")
    public ResponseEntity<Void> resetPhoto() {
        profilePhotoService.reset(currentMemberProvider.requireMemberId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "프로필 사진 파일 조회", description = "profileImageUrl의 파일을 반환합니다.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사진 파일"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PROFILE_PHOTO_NOT_FOUND")})
    @GetMapping("/{memberId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long memberId) {
        MemberProfilePhoto photo = profilePhotoService.get(memberId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.noStore()).body(photo.getContent());
    }
}
