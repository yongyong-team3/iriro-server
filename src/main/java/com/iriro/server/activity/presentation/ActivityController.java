package com.iriro.server.activity.presentation;


import com.iriro.server.activity.application.ActivityService;
import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "활동 기록")
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(
            summary = "활동 기록 저장",
            description = "활동 결과와 이동 경로를 저장합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ActivityCreateResponse>> createActivity(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ActivityCreateRequest request
    ) {

        Long currentMemberId = currentMemberProvider.requireMemberId();

        ActivityCreateResponse responseData =
                activityService.createActivity(
                        currentMemberId,
                        idempotencyKey,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "활동이 저장되었습니다.",
                        responseData
                ));
    }
}