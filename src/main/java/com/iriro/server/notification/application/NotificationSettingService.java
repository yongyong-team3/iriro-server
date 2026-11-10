package com.iriro.server.notification.application;

import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.notification.domain.NotificationSetting;
import com.iriro.server.notification.infrastructure.NotificationSettingRepository;
import com.iriro.server.notification.presentation.NotificationSettingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;
    private final MemberRepository memberRepository;

    public NotificationSettingService(
            NotificationSettingRepository settingRepository,
            MemberRepository memberRepository
    ) {
        this.settingRepository = settingRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public NotificationSettingResponse get(Long memberId) {
        return settingRepository.findById(memberId)
                .map(this::toResponse)
                .orElseGet(() -> new NotificationSettingResponse(false, null));
    }

    @Transactional
    public NotificationSettingResponse update(Long memberId, boolean enabled) {
        NotificationSetting setting = settingRepository.findById(memberId)
                .orElseGet(() -> new NotificationSetting(memberRepository.getReferenceById(memberId), enabled));
        setting.updateRecoveryForecastEnabled(enabled);
        return toResponse(settingRepository.save(setting));
    }

    private NotificationSettingResponse toResponse(NotificationSetting setting) {
        return new NotificationSettingResponse(setting.isRecoveryForecastEnabled(), setting.getUpdatedAt());
    }
}
