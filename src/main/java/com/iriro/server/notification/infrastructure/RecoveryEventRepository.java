package com.iriro.server.notification.infrastructure;

import com.iriro.server.notification.domain.RecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface RecoveryEventRepository extends JpaRepository<RecoveryEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from RecoveryEvent event where event.id = :id")
    Optional<RecoveryEvent> findLockedById(@Param("id") Long id);
}
