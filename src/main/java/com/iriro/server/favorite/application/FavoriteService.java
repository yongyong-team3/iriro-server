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
    /**
     * FAV-03 즐겨찾기 삭제
     */
    @Transactional
    public void deleteFavorite(Long memberId, Long favoriteId) {

        // 1. 회원 존재 여부 확인
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        // 2. 본인의 즐겨찾기 조회
        Favorite favorite = favoriteRepository
                .findByIdAndMemberId(favoriteId, memberId)
                .orElse(null);

        // 3. 본인의 즐겨찾기가 아닌 경우 확인
        if (favorite == null) {

            // 실제 존재하는 다른 회원의 즐겨찾기라면 404
            if (favoriteRepository.existsById(favoriteId)) {
                throw new CustomException(ErrorCode.FAVORITE_NOT_FOUND);
            }

            // 이미 삭제된 즐겨찾기라면 재요청이므로 성공 처리
            return;
        }

        // 4. 즐겨찾기 삭제
        favoriteRepository.delete(favorite);
    }
}