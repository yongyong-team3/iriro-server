package com.iriro.server.notification.application;

import com.iriro.server.common.application.ApiException;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.notification.domain.Device;
import com.iriro.server.notification.infrastructure.DeviceRepository;
import com.iriro.server.notification.presentation.DeviceRegistrationRequest;
import com.iriro.server.notification.presentation.DeviceResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final MemberRepository memberRepository;

    public DeviceService(DeviceRepository deviceRepository, MemberRepository memberRepository) {
        this.deviceRepository = deviceRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public DeviceResponse register(Long memberId, DeviceRegistrationRequest request) {
        Device device = deviceRepository.findByInstallationId(request.installationId()).orElse(null);
        if (device != null) {
            if (!device.getMember().getId().equals(memberId)) {
                throw new ApiException(HttpStatus.CONFLICT, "INSTALLATION_IN_USE", "다른 회원에게 등록된 기기입니다.", "installationId");
            }
            device.updateRegistration(request.platform(), request.expoPushToken());
            return toResponse(device);
        }

        Device newDevice = new Device(
                memberRepository.getReferenceById(memberId),
                request.installationId(),
                request.platform(),
                request.expoPushToken()
        );
        try {
            return toResponse(deviceRepository.saveAndFlush(newDevice));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "INSTALLATION_IN_USE", "이미 등록된 기기입니다.", "installationId");
        }
    }

    @Transactional
    public void unregister(Long memberId, Long deviceId) {
        Device device = deviceRepository.findByIdAndMember_Id(deviceId, memberId)
                .orElseThrow(this::notFound);
        deviceRepository.delete(device);
    }

    private DeviceResponse toResponse(Device device) {
        return new DeviceResponse(device.getId(), device.getInstallationId(), device.getPlatform());
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "등록된 기기를 찾을 수 없습니다.");
    }
}
