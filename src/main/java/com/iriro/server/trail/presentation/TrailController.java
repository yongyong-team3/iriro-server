package com.iriro.server.trail.presentation;

import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.trail.application.TrailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trails")
@Tag(name = "하천길")
public class TrailController {
    private final TrailService service;

    public TrailController(TrailService service) {
        this.service = service;
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
}
