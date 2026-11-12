package com.iriro.server.river.presentation;

import com.iriro.server.common.ApiResponse;
import com.iriro.server.river.application.HistoricalWaterStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/water-levels")
@Tag(name = "수위 기록")
public class HistoricalWaterStatusController {
    private final HistoricalWaterStatusService service;

    public HistoricalWaterStatusController(HistoricalWaterStatusService service) {
        this.service = service;
    }

    @GetMapping("/history")
    @Operation(summary = "과거 수위·AI 판정 조회",
            description = "요청 시각 이전 10분 내에 관측·판정·저장된 스냅샷만 조회합니다. 재계산이나 실시간 결과를 사용하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장된 과거 스냅샷"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HISTORICAL_SNAPSHOT_NOT_FOUND")})
    public ApiResponse<HistoricalWaterStatusResponse> history(
            @Parameter(required = true, description = "조회 시각(UTC 오프셋 필수)", example = "2024-07-18T12:05:00+09:00")
            @RequestParam(required = false) String at,
            @Parameter(description = "관측소 코드", example = "1016607")
            @RequestParam(defaultValue = "1016607") String stationCode) {
        return ApiResponse.success("조회되었습니다.", service.get(at, stationCode));
    }
}
