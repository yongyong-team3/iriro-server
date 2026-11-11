package com.iriro.server.notification.infrastructure;

import com.iriro.server.notification.domain.PendingPushReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingPushReceiptRepository extends JpaRepository<PendingPushReceipt, String> { }
