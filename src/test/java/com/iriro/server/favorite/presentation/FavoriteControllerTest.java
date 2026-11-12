package com.iriro.server.favorite.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.auth.infrastructure.JwtAccessTokenFilter;
import com.iriro.server.favorite.application.FavoriteAddResult;
import com.iriro.server.favorite.application.FavoriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시큐리티 필터는 끄고(addFilters = false), 인증된 회원은 CurrentMemberProvider mock 으로 대체합니다.
 * ApiResponse 의 JSON 필드명이 code / message / data 라고 가정했습니다. 다르면 jsonPath 를 수정하세요.
 * Spring Boot 4 기준 import 입니다. 패키지가 다르면 IDE 자동 import 로 맞추세요.
 */
@WebMvcTest(
        controllers = FavoriteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAccessTokenFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class FavoriteControllerTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;


    @MockitoBean
    private FavoriteService favoriteService;

    @MockitoBean
    private CurrentMemberProvider currentMemberProvider;

    @BeforeEach
    void setUp() {
        given(currentMemberProvider.requireMemberId()).willReturn(MEMBER_ID);
    }

    // ------------------------------------------------------------------
    // FAV-01 목록 조회
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/favorites - 200, 목록 반환")
    void getFavorites() throws Exception {
        FavoriteResponse item = FavoriteResponse.builder()
                .favoriteId(1000L)
                .routeId(10L)
                .routeName("테스트 코스")
                .trailId(100L)
                .trailName("테스트 하천길")
                .distanceMeters(new BigDecimal("1234.5"))
                .geometry(new FavoriteGeometry("LineString", List.of(
                        List.of(new BigDecimal("127.2355"), new BigDecimal("37.3245")))))
                .build();
        given(favoriteService.getFavorites(MEMBER_ID))
                .willReturn(FavoriteListResponse.builder().favorites(List.of(item)).build());

        mockMvc.perform(get("/api/v1/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("조회되었습니다."))
                .andExpect(jsonPath("$.data.favorites[0].favoriteId").value(1000))
                .andExpect(jsonPath("$.data.favorites[0].routeId").value(10))
                .andExpect(jsonPath("$.data.favorites[0].routeName").value("테스트 코스"))
                .andExpect(jsonPath("$.data.favorites[0].trailId").value(100))
                .andExpect(jsonPath("$.data.favorites[0].trailName").value("테스트 하천길"))
                .andExpect(jsonPath("$.data.favorites[0].distanceMeters").value(1234.5))
                .andExpect(jsonPath("$.data.favorites[0].geometry.type").value("LineString"))
                .andExpect(jsonPath("$.data.favorites[0].geometry.coordinates[0][0]").value(127.2355));
    }

    @Test
    @DisplayName("GET /api/v1/favorites - 빈 목록도 200")
    void getFavoritesEmpty() throws Exception {
        given(favoriteService.getFavorites(MEMBER_ID))
                .willReturn(FavoriteListResponse.builder().favorites(List.of()).build());

        mockMvc.perform(get("/api/v1/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorites").isEmpty());
    }

    // ------------------------------------------------------------------
    // FAV-02 즐겨찾기 추가
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/favorites - 신규 저장이면 201")
    void addFavoriteCreated() throws Exception {
        given(favoriteService.addFavorite(eq(MEMBER_ID), any(FavoriteCreateRequest.class)))
                .willReturn(new FavoriteAddResult(FavoriteCreateResponse.of(1000L, 10L), true));

        mockMvc.perform(post("/api/v1/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routeId\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("즐겨찾기에 저장되었습니다."))
                .andExpect(jsonPath("$.data.favoriteId").value(1000))
                .andExpect(jsonPath("$.data.routeId").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/favorites - 이미 저장된 코스면 200, 같은 favoriteId")
    void addFavoriteAlreadyExists() throws Exception {
        given(favoriteService.addFavorite(eq(MEMBER_ID), any(FavoriteCreateRequest.class)))
                .willReturn(new FavoriteAddResult(FavoriteCreateResponse.of(1000L, 10L), false));

        mockMvc.perform(post("/api/v1/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routeId\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("즐겨찾기에 저장되었습니다."))
                .andExpect(jsonPath("$.data.favoriteId").value(1000))
                .andExpect(jsonPath("$.data.routeId").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/favorites - routeId 누락 시 400")
    void addFavoriteMissingRouteId() throws Exception {
        mockMvc.perform(post("/api/v1/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(favoriteService, never()).addFavorite(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/favorites - routeId 가 0 이하면 400")
    void addFavoriteNonPositiveRouteId() throws Exception {
        mockMvc.perform(post("/api/v1/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routeId\": 0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routeId\": -5}"))
                .andExpect(status().isBadRequest());

        verify(favoriteService, never()).addFavorite(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/favorites - 본문이 없으면 400")
    void addFavoriteNoBody() throws Exception {
        mockMvc.perform(post("/api/v1/favorites")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/favorites - routeId 타입이 잘못되면 400")
    void addFavoriteInvalidType() throws Exception {
        mockMvc.perform(post("/api/v1/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routeId\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // FAV-03 즐겨찾기 삭제
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/v1/favorites/{id} - 204")
    void deleteFavorite() throws Exception {
        mockMvc.perform(delete("/api/v1/favorites/{favoriteId}", 1000L))
                .andExpect(status().isNoContent());

        verify(favoriteService).deleteFavorite(MEMBER_ID, 1000L);
    }

    @Test
    @DisplayName("DELETE /api/v1/favorites/{id} - id 가 숫자가 아니면 400")
    void deleteFavoriteInvalidId() throws Exception {
        mockMvc.perform(delete("/api/v1/favorites/{favoriteId}", "abc"))
                .andExpect(status().isBadRequest());

        verify(favoriteService, never()).deleteFavorite(any(), any());
    }
}
