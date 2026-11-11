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

        validateMember(memberId);

        List<FavoriteResponse> favorites =
                favoriteRepository.findAllWithRouteByMemberId(memberId)
                        .stream()
                        .map(favorite -> {

                            Route route = favorite.getRoute();

                            Trail trail = trailRepository.findById(route.getTrailId())
                                    .orElseThrow(() ->
                                            new CustomException(ErrorCode.TRAIL_NOT_FOUND)
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

    /**
     * FAV-02 즐겨찾기 추가
     * FavoriteController에서 호출
     */
    @Transactional
    public FavoriteAddResult addFavorite(
            Long memberId,
            FavoriteCreateRequest request
    ) {

        validateMember(memberId);

        if (request == null || request.getRouteId() == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        Long routeId = request.getRouteId();

        Route route = routeRepository.findById(routeId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.ROUTE_NOT_FOUND)
                );

        // 이미 즐겨찾기한 경우 기존 데이터 반환
        Favorite existing = favoriteRepository
                .findByMemberIdAndRouteId(memberId, routeId)
                .orElse(null);

        if (existing != null) {
            return new FavoriteAddResult(
                    FavoriteCreateResponse.of(existing.getId(), routeId),
                    false
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.UNAUTHORIZED_USER)
                );

        Favorite saved = favoriteRepository.save(
                new Favorite(member, route)
        );

        return new FavoriteAddResult(
                FavoriteCreateResponse.of(saved.getId(), routeId),
                true
        );
    }

    /**
     * ACT-01 활동 저장 시 즐겨찾기 추가
     * ActivityService에서 호출
     *
     * @return 새롭게 추가된 경우 true
     */
    @Transactional
    public boolean addFavorite(Long memberId, Long routeId) {

        validateMember(memberId);

        Route route = routeRepository.findById(routeId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.ROUTE_NOT_FOUND)
                );

        // 이미 즐겨찾기한 경우 추가하지 않음
        if (favoriteRepository.findByMemberIdAndRouteId(memberId, routeId)
                .isPresent()) {
            return false;
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.UNAUTHORIZED_USER)
                );

        favoriteRepository.save(new Favorite(member, route));

        return true;
    }

    /**
     * FAV-03 즐겨찾기 삭제
     */
    @Transactional
    public void deleteFavorite(Long memberId, Long favoriteId) {

        validateMember(memberId);

        Favorite favorite = favoriteRepository
                .findByIdAndMemberId(favoriteId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAVORITE_NOT_FOUND));

        favoriteRepository.delete(favorite);
    }

    /**
     * 회원 존재 여부 검증
     */
    private void validateMember(Long memberId) {

        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }
    }
}