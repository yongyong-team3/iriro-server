package com.iriro.server.notification.infrastructure;

import com.iriro.server.notification.domain.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
}
