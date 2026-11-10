package com.iriro.server.notification.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.notification.application.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "알림")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentMemberProvider currentMemberProvider;

    public NotificationController(NotificationService notificationService, CurrentMemberProvider currentMemberProvider) {
        this.notificationService = notificationService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @GetMapping
    @Operation(summary = "알림 목록 조회")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "알림 목록"),
            @ApiResponse(responseCode = "400", description = "조회 조건 오류"),
            @ApiResponse(responseCode = "401", description = "인증 실패")})
    public ApiSuccessResponse<NotificationListResponse> list(
            @RequestParam(required = false) @Positive Long beforeId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ApiSuccessResponse.of("NOTIFICATION_LIST_SUCCESS", "알림 목록을 조회했습니다.",
                notificationService.list(currentMemberProvider.requireMemberId(), beforeId, limit));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "읽음 처리"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "알림 없음")})
    public ApiSuccessResponse<NotificationReadResponse> markRead(@PathVariable @Positive Long notificationId) {
        return ApiSuccessResponse.of("NOTIFICATION_READ_SUCCESS", "알림을 읽음 처리했습니다.",
                notificationService.markRead(currentMemberProvider.requireMemberId(), notificationId));
    }
}
