package com.iriro.server.activity.presentation;

import com.iriro.server.activity.application.ActivityService;
import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.ApiResponse;
import com.iriro.server.common.application.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 충돌 해결본
 *  - ACT-01 저장(POST): dev 구현 그대로 (본문을 JsonNode 로 받아 simulated/saveAsFavorite 타입을 직접 검증)
 *  - ACT-02~05 조회(GET): 기존 구현 유지
 */
@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "활동")
public class ActivityController {
    private final ActivityService service;
    private final CurrentMemberProvider currentMemberProvider;
    private final ObjectMapper mapper;
    private final Validator validator;

    public ActivityController(ActivityService service, CurrentMemberProvider currentMemberProvider,
                              ObjectMapper mapper, Validator validator) {
        this.service = service;
        this.currentMemberProvider = currentMemberProvider;
        this.mapper = mapper;
        this.validator = validator;
    }

    // =====================================================================
    // ACT-01 활동 기록 저장 (dev)
    // =====================================================================

    @PostMapping
    @Operation(summary = "활동 기록 저장", description = "실제 활동의 배지 획득을 갱신합니다. 시연 기록은 허용된 테스트 환경에서만 저장할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "활동 저장 또는 동일 요청 재시도"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "IDEMPOTENCY_CONFLICT")})
    public ResponseEntity<ApiResponse<ActivitySaveResponse>> save(
            @Parameter(required = true, description = "요청별 UUID") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content =
            @io.swagger.v3.oas.annotations.media.Content(schema =
            @io.swagger.v3.oas.annotations.media.Schema(implementation = ActivitySaveRequest.class)))
            @RequestBody JsonNode body) {
        if (!body.isObject() || (body.has("simulated") && !body.get("simulated").isBoolean())
                || (body.has("saveAsFavorite") && !body.get("saveAsFavorite").isBoolean())) {
            throw invalid();
        }
        ActivitySaveRequest request;
        try {
            request = mapper.treeToValue(body, ActivitySaveRequest.class);
        } catch (RuntimeException exception) {
            throw invalid();
        }
        if (!validator.validate(request).isEmpty()) throw invalid();
        ActivitySaveResponse result = service.save(currentMemberProvider.requireMemberId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("활동이 저장되었습니다.", result));
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
    }

    // =====================================================================
    // ACT-02 활동 기록 목록 조회
    // =====================================================================

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
                service.getActivities(
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

    // =====================================================================
    // ACT-03 활동 기록 상세 조회
    // =====================================================================

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
                service.getActivityDetail(
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

    // =====================================================================
    // ACT-04 주간 활동 통계 조회
    // =====================================================================

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
                service.getWeeklyStats(
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

    // =====================================================================
    // ACT-05 월간 활동 통계 조회
    // =====================================================================

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
                service.getMonthlyStats(
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