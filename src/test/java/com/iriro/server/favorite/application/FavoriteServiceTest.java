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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private RouteRepository routeRepository;
    @Mock
    private TrailRepository trailRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    private static final Long MEMBER_ID = 1L;
    private static final Long ROUTE_ID = 10L;
    private static final Long TRAIL_ID = 100L;
    private static final Long FAVORITE_ID = 1000L;

    private static void assertErrorCode(Throwable t, ErrorCode expected) {
        assertThat(t)
                .isInstanceOf(CustomException.class)
                // CustomException의 필드명이 errorCode가 아니라면 이 부분을 수정하세요.
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private FavoriteCreateRequest createRequest(Long routeId) {
        FavoriteCreateRequest request = new FavoriteCreateRequest();
        ReflectionTestUtils.setField(request, "routeId", routeId);
        return request;
    }

    // ------------------------------------------------------------------
    // FAV-01 즐겨찾기 목록 조회
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("FAV-01 즐겨찾기 목록 조회")
    class GetFavorites {

        @Test
        @DisplayName("memberId가 null이면 UNAUTHORIZED_USER 예외")
        void nullMemberId() {
            assertThatThrownBy(() -> favoriteService.getFavorites(null))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.UNAUTHORIZED_USER));

            verify(favoriteRepository, never()).findAllWithRouteByMemberId(any());
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 UNAUTHORIZED_USER 예외")
        void memberNotFound() {
            given(memberRepository.existsById(MEMBER_ID)).willReturn(false);

            assertThatThrownBy(() -> favoriteService.getFavorites(MEMBER_ID))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.UNAUTHORIZED_USER));

            verify(favoriteRepository, never()).findAllWithRouteByMemberId(any());
        }

        @Test
        @DisplayName("즐겨찾기가 없으면 빈 목록 반환")
        void emptyFavorites() {
            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(favoriteRepository.findAllWithRouteByMemberId(MEMBER_ID)).willReturn(List.of());

            FavoriteListResponse response = favoriteService.getFavorites(MEMBER_ID);

            assertThat(response.getFavorites()).isEmpty();
        }

        @Test
        @DisplayName("즐겨찾기 목록을 코스/하천길 정보와 함께 반환")
        void success() {
            Route route = mock(Route.class);
            given(route.getId()).willReturn(ROUTE_ID);
            given(route.getTrailId()).willReturn(TRAIL_ID);
            given(route.getName()).willReturn("테스트 코스");
            given(route.getDistanceMeters()).willReturn(new BigDecimal("1234.5"));
            given(route.getGeometryGeojson()).willReturn("{\"type\":\"LineString\"}");

            Trail trail = mock(Trail.class);
            given(trail.getId()).willReturn(TRAIL_ID);
            given(trail.getName()).willReturn("테스트 하천길");

            Favorite favorite = mock(Favorite.class);
            given(favorite.getId()).willReturn(FAVORITE_ID);
            given(favorite.getRoute()).willReturn(route);

            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(favoriteRepository.findAllWithRouteByMemberId(MEMBER_ID)).willReturn(List.of(favorite));
            given(trailRepository.findById(TRAIL_ID)).willReturn(Optional.of(trail));

            FavoriteListResponse response = favoriteService.getFavorites(MEMBER_ID);

            assertThat(response.getFavorites()).hasSize(1);
            FavoriteResponse item = response.getFavorites().get(0);
            assertThat(item.getFavoriteId()).isEqualTo(FAVORITE_ID);
            assertThat(item.getRouteId()).isEqualTo(ROUTE_ID);
            assertThat(item.getRouteName()).isEqualTo("테스트 코스");
            assertThat(item.getTrailId()).isEqualTo(TRAIL_ID);
            assertThat(item.getTrailName()).isEqualTo("테스트 하천길");
            assertThat(item.getDistanceMeters()).isEqualByComparingTo("1234.5");
            assertThat(item.getGeometry()).isEqualTo("{\"type\":\"LineString\"}");
        }

        @Test
        @DisplayName("코스의 하천길이 없으면 TRAIL_NOT_FOUND 예외")
        void trailNotFound() {
            Route route = mock(Route.class);
            given(route.getTrailId()).willReturn(TRAIL_ID);

            Favorite favorite = mock(Favorite.class);
            given(favorite.getRoute()).willReturn(route);

            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(favoriteRepository.findAllWithRouteByMemberId(MEMBER_ID)).willReturn(List.of(favorite));
            given(trailRepository.findById(TRAIL_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.getFavorites(MEMBER_ID))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.TRAIL_NOT_FOUND));
        }
    }

    // ------------------------------------------------------------------
    // FAV-02 즐겨찾기 추가
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("FAV-02 즐겨찾기 추가")
    class AddFavorite {

        @Test
        @DisplayName("존재하지 않는 회원이면 UNAUTHORIZED_USER 예외")
        void memberNotFound() {
            given(memberRepository.existsById(MEMBER_ID)).willReturn(false);

            assertThatThrownBy(() -> favoriteService.addFavorite(MEMBER_ID, createRequest(ROUTE_ID)))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.UNAUTHORIZED_USER));

            verify(favoriteRepository, never()).save(any());
        }

        @Test
        @DisplayName("memberId가 null이면 UNAUTHORIZED_USER 예외")
        void nullMemberId() {
            assertThatThrownBy(() -> favoriteService.addFavorite(null, createRequest(ROUTE_ID)))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.UNAUTHORIZED_USER));
        }

        @Test
        @DisplayName("코스가 없으면 ROUTE_NOT_FOUND 예외")
        void routeNotFound() {
            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(routeRepository.findById(ROUTE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.addFavorite(MEMBER_ID, createRequest(ROUTE_ID)))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.ROUTE_NOT_FOUND));

            verify(favoriteRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 즐겨찾기한 코스면 저장하지 않고 기존 정보를 반환")
        void alreadyExists() {
            Route route = mock(Route.class);
            Favorite existing = mock(Favorite.class);
            given(existing.getId()).willReturn(FAVORITE_ID);

            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(routeRepository.findById(ROUTE_ID)).willReturn(Optional.of(route));
            given(favoriteRepository.findByMemberIdAndRouteId(MEMBER_ID, ROUTE_ID))
                    .willReturn(Optional.of(existing));

            FavoriteAddResult result = favoriteService.addFavorite(MEMBER_ID, createRequest(ROUTE_ID));

            assertThat(result.created()).isFalse();
            assertThat(result.response().getFavoriteId()).isEqualTo(FAVORITE_ID);
            assertThat(result.response().getRouteId()).isEqualTo(ROUTE_ID);
            verify(favoriteRepository, never()).save(any());
            verify(memberRepository, never()).findById(any());
        }

        @Test
        @DisplayName("신규 즐겨찾기면 저장 후 응답 반환")
        void createNew() {
            Route route = mock(Route.class);
            Member member = mock(Member.class);
            Favorite saved = mock(Favorite.class);
            given(saved.getId()).willReturn(FAVORITE_ID);

            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(routeRepository.findById(ROUTE_ID)).willReturn(Optional.of(route));
            given(favoriteRepository.findByMemberIdAndRouteId(MEMBER_ID, ROUTE_ID))
                    .willReturn(Optional.empty());
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
            given(favoriteRepository.save(any(Favorite.class))).willReturn(saved);

            FavoriteAddResult result = favoriteService.addFavorite(MEMBER_ID, createRequest(ROUTE_ID));

            assertThat(result.created()).isTrue();
            assertThat(result.response().getFavoriteId()).isEqualTo(FAVORITE_ID);
            assertThat(result.response().getRouteId()).isEqualTo(ROUTE_ID);
            verify(favoriteRepository).save(any(Favorite.class));
        }

        @Test
        @DisplayName("신규 저장 시 회원 조회에 실패하면 UNAUTHORIZED_USER 예외")
        void memberDisappearsBeforeSave() {
            Route route = mock(Route.class);

            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(routeRepository.findById(ROUTE_ID)).willReturn(Optional.of(route));
            given(favoriteRepository.findByMemberIdAndRouteId(MEMBER_ID, ROUTE_ID))
                    .willReturn(Optional.empty());
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> favoriteService.addFavorite(MEMBER_ID, createRequest(ROUTE_ID)))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.UNAUTHORIZED_USER));

            verify(favoriteRepository, never()).save(any());
        }
    }

    // ------------------------------------------------------------------
    // FAV-03 즐겨찾기 삭제
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("FAV-03 즐겨찾기 삭제")
    class DeleteFavorite {

        @Test
        @DisplayName("존재하지 않는 회원이면 UNAUTHORIZED_USER 예외")
        void memberNotFound() {
            given(memberRepository.existsById(MEMBER_ID)).willReturn(false);

            assertThatThrownBy(() -> favoriteService.deleteFavorite(MEMBER_ID, FAVORITE_ID))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.UNAUTHORIZED_USER));

            verify(favoriteRepository, never()).delete(any());
        }

        @Test
        @DisplayName("본인의 즐겨찾기를 삭제")
        void deleteOwn() {
            Favorite favorite = mock(Favorite.class);

            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(favoriteRepository.findByIdAndMemberId(FAVORITE_ID, MEMBER_ID))
                    .willReturn(Optional.of(favorite));

            favoriteService.deleteFavorite(MEMBER_ID, FAVORITE_ID);

            verify(favoriteRepository).delete(favorite);
        }

        @Test
        @DisplayName("다른 회원의 즐겨찾기면 FAVORITE_NOT_FOUND 예외")
        void otherMembersFavorite() {
            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(favoriteRepository.findByIdAndMemberId(FAVORITE_ID, MEMBER_ID))
                    .willReturn(Optional.empty());
            given(favoriteRepository.existsById(FAVORITE_ID)).willReturn(true);

            assertThatThrownBy(() -> favoriteService.deleteFavorite(MEMBER_ID, FAVORITE_ID))
                    .satisfies(t -> assertErrorCode(t, ErrorCode.FAVORITE_NOT_FOUND));

            verify(favoriteRepository, never()).delete(any());
        }

        @Test
        @DisplayName("이미 삭제된 즐겨찾기면 예외 없이 성공 처리(멱등)")
        void alreadyDeleted() {
            given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
            given(favoriteRepository.findByIdAndMemberId(FAVORITE_ID, MEMBER_ID))
                    .willReturn(Optional.empty());
            given(favoriteRepository.existsById(FAVORITE_ID)).willReturn(false);

            assertThatCode(() -> favoriteService.deleteFavorite(MEMBER_ID, FAVORITE_ID))
                    .doesNotThrowAnyException();

            verify(favoriteRepository, never()).delete(any());
        }
    }
}