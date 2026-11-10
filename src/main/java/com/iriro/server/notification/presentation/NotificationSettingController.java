package com.iriro.server.notification.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.notification.application.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "알림")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/notification-settings")
public class NotificationSettingController {

    private final NotificationSettingService settingService;
    private final CurrentMemberProvider currentMemberProvider;

    public NotificationSettingController(NotificationSettingService settingService, CurrentMemberProvider currentMemberProvider) {
        this.settingService = settingService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @GetMapping
    @Operation(summary = "회복 예측 알림 설정 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "알림 설정"),
            @ApiResponse(responseCode = "401", description = "인증 실패")})
    public ApiSuccessResponse<NotificationSettingResponse> get() {
        return ApiSuccessResponse.of("NOTIFICATION_SETTING_SUCCESS", "알림 설정을 조회했습니다.",
                settingService.get(currentMemberProvider.requireMemberId()));
    }

    @PatchMapping
    @Operation(summary = "회복 예측 알림 설정 변경")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "변경 완료"),
            @ApiResponse(responseCode = "400", description = "입력 오류"),
            @ApiResponse(responseCode = "401", description = "인증 실패")})
    public ApiSuccessResponse<NotificationSettingResponse> update(@Valid @RequestBody NotificationSettingUpdateRequest request) {
        return ApiSuccessResponse.of("NOTIFICATION_SETTING_UPDATE_SUCCESS", "알림 설정을 변경했습니다.",
                settingService.update(currentMemberProvider.requireMemberId(), request.recoveryForecastEnabled()));
    }
}
