package com.iriro.server.trail.presentation;

import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.trail.application.TrailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trails")
public class TrailController {
    private final TrailService service;

    public TrailController(TrailService service) {
        this.service = service;
    }

    @GetMapping
    public ApiSuccessResponse<TrailListResponse> list(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) String query
    ) {
        return ApiSuccessResponse.of("SUCCESS", "조회되었습니다.", service.list(latitude, longitude, query));
    }
}
