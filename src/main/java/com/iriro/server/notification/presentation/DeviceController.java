package com.iriro.server.notification.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.notification.application.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "푸시 기기")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final CurrentMemberProvider currentMemberProvider;

    public DeviceController(DeviceService deviceService, CurrentMemberProvider currentMemberProvider) {
        this.deviceService = deviceService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @PostMapping
    @Operation(summary = "Expo 푸시 기기 등록")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "기기 등록"),
            @ApiResponse(responseCode = "400", description = "입력 오류"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "409", description = "다른 회원의 기기")})
    public ResponseEntity<ApiSuccessResponse<DeviceResponse>> register(@Valid @RequestBody DeviceRegistrationRequest request) {
        DeviceResponse response = deviceService.register(currentMemberProvider.requireMemberId(), request);
        return ResponseEntity.ok(ApiSuccessResponse.of("DEVICE_REGISTER_SUCCESS", "기기를 등록했습니다.", response));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "푸시 기기 해제")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "해제 완료"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "기기 없음")})
    public ResponseEntity<Void> unregister(@PathVariable @Positive Long deviceId) {
        deviceService.unregister(currentMemberProvider.requireMemberId(), deviceId);
        return ResponseEntity.noContent().build();
    }
}
