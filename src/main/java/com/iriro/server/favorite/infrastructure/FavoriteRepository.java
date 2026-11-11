package com.iriro.server.favorite.infrastructure;

import com.iriro.server.favorite.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @Query("""
    SELECT f
    FROM Favorite f
    JOIN FETCH f.route r
    WHERE f.member.id = :memberId
""")
    List<Favorite> findAllWithRouteByMemberId(
            @Param("memberId") Long memberId
    );

    Optional<Favorite> findByMemberIdAndRouteId(
            Long memberId,
            Long routeId
    );

    Optional<Favorite> findByIdAndMemberId(
            Long favoriteId,
            Long memberId
    );
}