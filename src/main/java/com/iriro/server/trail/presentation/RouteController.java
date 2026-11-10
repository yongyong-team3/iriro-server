package com.iriro.server.trail.presentation;

import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.trail.application.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
@Tag(name = "코스")
public class RouteController {
    private final RouteService service;

    public RouteController(RouteService service) {
        this.service = service;
    }

    @GetMapping("/{routeId}")
    @Operation(summary = "코스 상세 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "코스 없음")
    public ApiSuccessResponse<RouteDetailResponse> detail(@PathVariable Long routeId) {
        return ApiSuccessResponse.of("SUCCESS", "조회되었습니다.", service.detail(routeId));
    }

    @GetMapping("/{routeId}/detours")
    @Operation(summary = "우회 경로 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공; 우회 경로가 없으면 빈 배열")
    @ApiResponse(responseCode = "404", description = "코스 없음")
    public ApiSuccessResponse<DetourListResponse> detours(@PathVariable Long routeId) {
        return ApiSuccessResponse.of("SUCCESS", "조회되었습니다.", service.detours(routeId));
    }

    @GetMapping("/{routeId}/availability")
    @Operation(summary = "코스 시작 전 상태 확인", description = "수위 경고 상태에도 시작은 허용하고 경고를 반환")
    @ApiResponse(responseCode = "200", description = "확인 성공")
    @ApiResponse(responseCode = "400", description = "지정 출발점 오류")
    @ApiResponse(responseCode = "404", description = "코스 없음")
    public ApiSuccessResponse<RouteAvailabilityResponse> availability(
            @PathVariable Long routeId,
            @Parameter(required = true, description = "지정 출발점 ID")
            @RequestParam(required = false) String startPointId) {
        return ApiSuccessResponse.of("SUCCESS", "조회되었습니다.", service.availability(routeId, startPointId));
    }
}
