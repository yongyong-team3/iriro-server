package com.iriro.server.trail.infrastructure;

import com.iriro.server.trail.domain.Detour;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DetourRepository extends JpaRepository<Detour, Long> {
    List<Detour> findByRouteIdOrderByIdAsc(Long routeId);
}
