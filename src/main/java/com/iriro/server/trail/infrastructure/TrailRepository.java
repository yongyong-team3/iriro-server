package com.iriro.server.trail.infrastructure;

import com.iriro.server.trail.domain.Trail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrailRepository extends JpaRepository<Trail, Long> {
}
