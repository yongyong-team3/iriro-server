package com.iriro.server.notification.infrastructure;

import com.iriro.server.notification.domain.RecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryEventRepository extends JpaRepository<RecoveryEvent, Long> { }
