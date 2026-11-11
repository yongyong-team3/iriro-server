package com.iriro.server.activity.presentation;


import com.iriro.server.activity.application.ActivityService;
import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    /**
     * ACT-02 활동 기록 목록 조회
     */
    @Operation(
            summary = "활동 기록 목록 조회",
            description = "월별 활동 기록을 최신순으로 조회합니다. 시연 기록도 포함됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping
    public ResponseEntity<ApiResponse<ActivityListResponse>> getActivities(

            @Parameter(description = "조회 월 (YYYY-MM)", example = "2026-11")
            @RequestParam(required = false)
            String month,

            @Parameter(description = "활동 유형 (WALK, RUN, BIKE)")
            @RequestParam(required = false)
            String activityType,

            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0")
            int page,

            @Parameter(description = "페이지 크기 (1~100)", example = "20")
            @RequestParam(defaultValue = "20")
            int size

    ) {

        Long currentMemberId = currentMemberProvider.requireMemberId();

        ActivityListResponse responseData =
                activityService.getActivities(
                        currentMemberId,
                        month,
                        activityType,
                        page,
                        size
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "조회되었습니다.",
                        responseData
                )
        );
    }
    /**
     * ACT-03 활동 기록 상세 조회
     */
    @Operation(
            summary = "활동 기록 상세 조회",
            description = "활동 기록의 상세 정보와 GPS 이동 경로를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/{activityId}")
    public ResponseEntity<ApiResponse<ActivityDetailResponse>> getActivityDetail(
            @PathVariable Long activityId
    ) {

        Long currentMemberId = currentMemberProvider.requireMemberId();

        ActivityDetailResponse responseData =
                activityService.getActivityDetail(
                        currentMemberId,
                        activityId
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "조회되었습니다.",
                        responseData
                )
        );
    }
    /**
     * ACT-04 주간 활동 통계 조회
     */
    @Operation(
            summary = "주간 활동 통계 조회",
            description = "주간 활동 거리, 횟수, 시간 및 최근 활동을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/stats/weekly")
    public ResponseEntity<ApiResponse<ActivityWeeklyStatsResponse>> getWeeklyStats(

            @Parameter(description = "조회 기준일 (yyyy-MM-dd)", example = "2026-11-11")
            @RequestParam(required = false)
            String date

    ) {

        Long currentMemberId = currentMemberProvider.requireMemberId();

        ActivityWeeklyStatsResponse responseData =
                activityService.getWeeklyStats(
                        currentMemberId,
                        date
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "조회되었습니다.",
                        responseData
                )
        );
    }
    /**
     * ACT-05 월간 활동 통계 조회
     */
    @Operation(
            summary = "월간 활동 통계 조회",
            description = "월간 활동 거리, 횟수, 시간 및 연속 활동 일수를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/stats/monthly")
    public ResponseEntity<ApiResponse<ActivityMonthlyStatsResponse>> getMonthlyStats(

            @Parameter(description = "조회 월 (YYYY-MM)", example = "2026-11")
            @RequestParam(required = false)
            String month

    ) {

        Long currentMemberId = currentMemberProvider.requireMemberId();

        ActivityMonthlyStatsResponse responseData =
                activityService.getMonthlyStats(
                        currentMemberId,
                        month
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "조회되었습니다.",
                        responseData
                )
        );
    }
}