package com.iriro.server.trail.infrastructure;

import com.iriro.server.trail.domain.Trail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TrailRepository extends JpaRepository<Trail, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trail from Trail trail where trail.id = :id")
    Optional<Trail> findLockedById(@Param("id") Long id);
}
