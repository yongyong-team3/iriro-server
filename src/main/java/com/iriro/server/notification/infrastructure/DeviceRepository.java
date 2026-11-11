package com.iriro.server.notification.infrastructure;

import com.iriro.server.notification.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByInstallationId(String installationId);

    Optional<Device> findByIdAndMember_Id(Long id, Long memberId);

    @Query(value = """
            SELECT DISTINCT d.id AS deviceId, d.member_id AS memberId, d.expo_push_token AS expoPushToken
            FROM devices d
            JOIN notification_settings ns ON ns.member_id = d.member_id
            WHERE ns.recovery_forecast_enabled = TRUE
              AND d.expo_push_token IS NOT NULL
              AND d.expo_push_token <> ''
            """, nativeQuery = true)
    java.util.List<RecoveryNotificationRecipient> findRecoveryRecipients();

}
