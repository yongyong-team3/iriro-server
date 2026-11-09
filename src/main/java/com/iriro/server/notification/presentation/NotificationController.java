package com.iriro.server.notification.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.notification.application.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentMemberProvider currentMemberProvider;

    public NotificationController(NotificationService notificationService, CurrentMemberProvider currentMemberProvider) {
        this.notificationService = notificationService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @GetMapping
    public ApiSuccessResponse<NotificationListResponse> list(
            @RequestParam(required = false) @Positive Long beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ApiSuccessResponse.of("NOTIFICATION_LIST_SUCCESS", "알림 목록을 조회했습니다.",
                notificationService.list(currentMemberProvider.requireMemberId(), beforeId, limit));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiSuccessResponse<NotificationReadResponse> markRead(@PathVariable @Positive Long notificationId) {
        return ApiSuccessResponse.of("NOTIFICATION_READ_SUCCESS", "알림을 읽음 처리했습니다.",
                notificationService.markRead(currentMemberProvider.requireMemberId(), notificationId));
    }
}
