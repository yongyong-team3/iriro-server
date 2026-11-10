package com.iriro.server.trail.infrastructure;

import com.iriro.server.trail.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {
    List<Route> findByTrailIdOrderByIdAsc(Long trailId);
}
