package com.iriro.server.favorite.application;

import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.favorite.domain.Favorite;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.favorite.presentation.FavoriteCreateRequest;
import com.iriro.server.favorite.presentation.FavoriteCreateResponse;
import com.iriro.server.favorite.presentation.FavoriteListResponse;
import com.iriro.server.favorite.presentation.FavoriteResponse;
import com.iriro.server.favorite.presentation.FavoriteGeometry;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.RouteRepository;
import com.iriro.server.trail.infrastructure.TrailRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
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
    private final ObjectMapper objectMapper;

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
                                    .geometry(objectMapper.readValue(route.getGeometryGeojson(), FavoriteGeometry.class))
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
    public FavoriteAddResult addFavorite(Long memberId, FavoriteCreateRequest request) {

        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_USER);
        }

        Long routeId = request.getRouteId();

        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROUTE_NOT_FOUND));

        return favoriteRepository.findByMemberIdAndRouteId(memberId, routeId)
                .map(favorite -> new FavoriteAddResult(
                        FavoriteCreateResponse.of(favorite.getId(), routeId), false))
                .orElseGet(() -> {
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED_USER));

                    Favorite saved = favoriteRepository.save(new Favorite(member, route));

                    return new FavoriteAddResult(
                            FavoriteCreateResponse.of(saved.getId(), routeId), true);
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
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "대상을 찾을 수 없습니다.");
            }

            // 이미 삭제된 즐겨찾기라면 재요청이므로 성공 처리
            return;
        }

        // 4. 즐겨찾기 삭제
        favoriteRepository.delete(favorite);
    }
}
