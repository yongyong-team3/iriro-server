package com.iriro.server.favorite.application;

import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.favorite.domain.Favorite;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.favorite.presentation.FavoriteCreateRequest;
import com.iriro.server.favorite.presentation.FavoriteCreateResponse;
import com.iriro.server.favorite.presentation.FavoriteListResponse;
import com.iriro.server.favorite.presentation.FavoriteResponse;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.RouteRepository;
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
    private final MemberRepository memberRepository;
    private final RouteRepository routeRepository;
    private final TrailRepository trailRepository;

    /**
     * FAV-01 즐겨찾기 목록 조회
     */
    public FavoriteListResponse getFavorites(Long memberId) {

        // 1. 회원 존재 여부 확인
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        // 2. 회원의 즐겨찾기 목록 조회
        List<FavoriteResponse> favorites =
                favoriteRepository.findAllWithRouteByMemberId(memberId)
                        .stream()
                        .map(favorite -> {

                            Route route = favorite.getRoute();

                            // 3. 하천길 조회
                            Trail trail = trailRepository.findById(route.getTrailId())
                                    .orElseThrow(() ->
                                            new CustomException(ErrorCode.TRAIL_NOT_FOUND)
                                    );

                            // 4. 응답 DTO 생성
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

    /**
     * FAV-02 즐겨찾기 추가
     */
    @Transactional
    public FavoriteCreateResponse addFavorite(
            Long memberId,
            FavoriteCreateRequest request
    ) {

        // 1. 회원 존재 여부 확인
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        Long routeId = request.getRouteId();

        // 2. 등록 코스 존재 여부 확인
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.ROUTE_NOT_FOUND)
                );

        // 3. 기존 즐겨찾기 여부 확인
        return favoriteRepository.findByMemberIdAndRouteId(memberId, routeId)
                .map(favorite -> FavoriteCreateResponse.of(
                        favorite.getId(),
                        routeId
                ))
                .orElseGet(() -> {

                    // 4. 회원 조회
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() ->
                                    new CustomException(ErrorCode.UNAUTHORIZED_USER)
                            );

                    // 5. 즐겨찾기 생성
                    Favorite favorite = new Favorite(member, route);

                    Favorite savedFavorite =
                            favoriteRepository.save(favorite);

                    // 6. 응답 반환
                    return FavoriteCreateResponse.of(
                            savedFavorite.getId(),
                            routeId
                    );
                });
    }
}