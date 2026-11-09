package com.iriro.server.notification.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import com.iriro.server.notification.application.DeviceService;
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
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final CurrentMemberProvider currentMemberProvider;

    public DeviceController(DeviceService deviceService, CurrentMemberProvider currentMemberProvider) {
        this.deviceService = deviceService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse<DeviceResponse>> register(@Valid @RequestBody DeviceRegistrationRequest request) {
        DeviceResponse response = deviceService.register(currentMemberProvider.requireMemberId(), request);
        return ResponseEntity.ok(ApiSuccessResponse.of("DEVICE_REGISTER_SUCCESS", "기기를 등록했습니다.", response));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> unregister(@PathVariable @Positive Long deviceId) {
        deviceService.unregister(currentMemberProvider.requireMemberId(), deviceId);
        return ResponseEntity.noContent().build();
    }
}
