package com.iriro.server.trail.infrastructure;

import com.iriro.server.trail.domain.RouteStartPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RouteStartPointRepository extends JpaRepository<RouteStartPoint, Long> {
    List<RouteStartPoint> findByRouteIdOrderByIdAsc(Long routeId);
}
