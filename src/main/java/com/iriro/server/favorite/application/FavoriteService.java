package com.iriro.server.favorite.application;

import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.favorite.presentation.FavoriteListResponse;
import com.iriro.server.favorite.presentation.FavoriteResponse;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.TrailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final TrailRepository trailRepository;

    public FavoriteListResponse getFavorites(Long memberId) {

        List<FavoriteResponse> favorites =
                favoriteRepository.findAllWithRouteByMemberId(memberId)
                        .stream()
                        .map(favorite -> {
                            Route route = favorite.getRoute();

                            Trail trail = trailRepository.findById(route.getTrailId())
                                    .orElseThrow(() ->
                                            new IllegalArgumentException("하천길을 찾을 수 없습니다.")
                                    );

                            return FavoriteResponse.builder()
                                    .favoriteId(favorite.getId())
                                    .routeId(route.getId())
                                    .routeName(route.getName())
                                    .trailId(trail.getId())
                                    .trailName(trail.getName())
                                    .distanceMeters(route.getDistanceMeters())
                                    .geometry(route.getGeometryGeojson())
                                    .build();
                        })
                        .toList();

        return FavoriteListResponse.builder()
                .favorites(favorites)
                .build();
    }
}