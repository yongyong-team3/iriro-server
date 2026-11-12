package com.iriro.server.activity.presentation;

import com.iriro.server.activity.application.ActivityService;
import com.iriro.server.activity.application.ActivityQueryService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "활동")
public class ActivityController {
    private final ActivityService service;
    private final ActivityQueryService queryService;
    private final CurrentMemberProvider currentMemberProvider;
    private final ObjectMapper mapper;
    private final Validator validator;

    public ActivityController(ActivityService service, ActivityQueryService queryService,
                              CurrentMemberProvider currentMemberProvider,
                              ObjectMapper mapper, Validator validator) {
        this.service = service;
        this.queryService = queryService;
        this.currentMemberProvider = currentMemberProvider;
        this.mapper = mapper;
        this.validator = validator;
    }

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

    @GetMapping
    @Operation(summary = "활동 기록 목록 조회", description = "본인 기록을 한국 시간 월 기준으로 최신순 페이지 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED")})
    public ResponseEntity<ApiResponse<ActivityListResponse>> list(
            @Parameter(description = "조회 월(YYYY-MM), 생략 시 한국 시간 현재 월", example = "2026-11")
            @RequestParam(required = false) String month,
            @Parameter(description = "활동 유형", schema = @io.swagger.v3.oas.annotations.media.Schema(
                    allowableValues = {"WALK", "RUN", "BIKE"}))
            @RequestParam(required = false) String activityType,
            @Parameter(description = "0부터 시작", schema = @io.swagger.v3.oas.annotations.media.Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 1~100", schema = @io.swagger.v3.oas.annotations.media.Schema(
                    minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.",
                queryService.list(currentMemberProvider.requireMemberId(), month, activityType, page, size)));
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "활동 기록 상세 조회", description = "본인 활동의 거리·페이스·GPS 경로를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "ACTIVITY_NOT_FOUND")})
    public ResponseEntity<ApiResponse<ActivityDetailResponse>> detail(@PathVariable Long activityId) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.",
                queryService.detail(currentMemberProvider.requireMemberId(), activityId)));
    }

    @GetMapping("/stats/weekly")
    @Operation(summary = "주간 활동 통계 조회", description = "한국 시간 월요일부터 일요일까지 실제 활동을 집계합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주간 통계"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED")})
    public ResponseEntity<ApiResponse<WeeklyActivityStatsResponse>> weekly(
            @Parameter(description = "조회 기준일(YYYY-MM-DD), 생략 시 한국 시간 오늘", example = "2026-11-09")
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.",
                queryService.weekly(currentMemberProvider.requireMemberId(), date)));
    }

    @GetMapping("/stats/monthly")
    @Operation(summary = "월간 활동 통계 조회", description = "선택 월의 실제 활동과 전체 기록의 현재·최장 연속 일수를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "월간 통계"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED")})
    public ResponseEntity<ApiResponse<MonthlyActivityStatsResponse>> monthly(
            @Parameter(description = "조회 월(YYYY-MM), 생략 시 한국 시간 현재 월", example = "2026-11")
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.",
                queryService.monthly(currentMemberProvider.requireMemberId(), month)));
    }
}
