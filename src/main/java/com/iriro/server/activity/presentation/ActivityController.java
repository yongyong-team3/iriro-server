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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
