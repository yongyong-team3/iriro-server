package com.iriro.server.trail.presentation;

import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.trail.application.TrailService;
import com.iriro.server.trail.application.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trails")
@Tag(name = "하천길")
public class TrailController {
    private final TrailService service;
    private final RouteService routes;

    public TrailController(TrailService service, RouteService routes) {
        this.service = service;
        this.routes = routes;
    }

    @GetMapping
    @Operation(summary = "하천길 목록 조회", description = "query로 하천명·구간명을 검색하고, 좌표로 거리 계산")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "좌표 오류")
    public ApiSuccessResponse<TrailListResponse> list(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String query
    ) {
        return ApiSuccessResponse.of("SUCCESS", "조회되었습니다.", service.list(latitude, longitude, query));
    }

    @GetMapping("/{trailId}")
    @Operation(summary = "하천길 상세 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "하천길 없음")
    public ApiSuccessResponse<TrailDetailResponse> detail(@PathVariable Long trailId) {
        return ApiSuccessResponse.of("SUCCESS", "조회되었습니다.", service.detail(trailId));
    }

    @GetMapping("/{trailId}/routes")
    @Operation(summary = "하천길 코스 조회", description = "activityType으로 WALK·RUN·BIKE 코스 필터링")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "활동 유형 오류")
    @ApiResponse(responseCode = "404", description = "하천길 없음")
    public ApiSuccessResponse<RouteListResponse> routes(@PathVariable Long trailId,
                                                        @RequestParam(required = false) String activityType) {
        return ApiSuccessResponse.of("SUCCESS", "조회되었습니다.", routes.list(trailId, activityType));
    }
}
