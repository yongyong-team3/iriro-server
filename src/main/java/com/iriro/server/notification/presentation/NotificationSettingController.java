package com.iriro.server.notification.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.notification.application.NotificationSettingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-settings")
public class NotificationSettingController {

    private final NotificationSettingService settingService;
    private final CurrentMemberProvider currentMemberProvider;

    public NotificationSettingController(NotificationSettingService settingService, CurrentMemberProvider currentMemberProvider) {
        this.settingService = settingService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @GetMapping
    public ApiSuccessResponse<NotificationSettingResponse> get() {
        return ApiSuccessResponse.of("NOTIFICATION_SETTING_SUCCESS", "알림 설정을 조회했습니다.",
                settingService.get(currentMemberProvider.requireMemberId()));
    }

    @PatchMapping
    public ApiSuccessResponse<NotificationSettingResponse> update(@Valid @RequestBody NotificationSettingUpdateRequest request) {
        return ApiSuccessResponse.of("NOTIFICATION_SETTING_UPDATE_SUCCESS", "알림 설정을 변경했습니다.",
                settingService.update(currentMemberProvider.requireMemberId(), request.recoveryForecastEnabled()));
    }
}
