package com.iriro.server.notification.infrastructure;

import com.iriro.server.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    List<NotificationSetting> findByRecoveryForecastEnabledTrue();
}
