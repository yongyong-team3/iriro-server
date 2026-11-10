package com.iriro.server.notification.infrastructure;

import com.iriro.server.notification.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMember_IdOrderByIdDesc(Long memberId, Pageable pageable);

    List<Notification> findByMember_IdAndIdLessThanOrderByIdDesc(
            Long memberId,
            Long beforeId,
            Pageable pageable
    );

    Optional<Notification> findByIdAndMember_Id(Long id, Long memberId);

    boolean existsByEventIdAndMember_Id(Long eventId, Long memberId);

    long countByMemberIdAndIsReadFalse(Long memberId);
}
