package com.iriro.server.activity.presentation;

import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.badge.application.BadgeService;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
// Spring Boot 4: 패키지가 org.springframework.boot.webmvc.test.autoconfigure 로 이동했습니다.
// Boot 3.x 라면 org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc 로 바꾸세요.
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 트랜잭션 밖에서 실행해야 하는 활동 저장(ACT-01) 테스트
 *
 *  - ACT-01-E03 활동 또는 즐겨찾기 저장 실패 시 둘 다 롤백된다
 *  - ACT-01-X07 코스가 지원하지 않는 활동 유형은 400 이고 아무것도 저장되지 않는다
 *  - ACT-01-X08 같은 Idempotency-Key 동시 요청도 활동은 1건만 저장된다
 *
 * 롤백·동시성은 "실제로 커밋/롤백되는 트랜잭션"에서만 확인할 수 있으므로
 * 이 클래스는 @Transactional 을 붙이지 않는다. 대신 @AfterEach 에서 직접 정리한다.
 * (테스트 DB 에 이 테스트용 데이터가 잠깐 커밋되었다가 삭제된다.)
 *
 * dev 의 ActivityService.save 는 한 트랜잭션 안에서
 *   회원 잠금 -> 즐겨찾기 저장 -> 활동 저장(saveAndFlush) -> 배지 지급
 * 순서로 처리한다. 마지막 단계인 배지 지급(BadgeService)을 실패시키면
 * 이미 저장된 즐겨찾기와 활동이 둘 다 롤백되는지 확인할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ActivityRollbackTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ActivityRepository activityRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean CurrentMemberProvider currentMemberProvider;
    @MockitoBean BadgeService badgeService;      // 기본은 아무 일도 안 함, E03 에서만 예외를 던지게 한다

    private Long memberId;
    private Long trailId;
    private Long routeId;
    private Long startPointId;
    private final List<Long> routeIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);   // member_name 최대 12자
        memberId = memberRepository.saveAndFlush(
                new Member("rb-" + suffix, ("rb-" + suffix).toLowerCase())).getId();
        trailId = createTrail("롤백 테스트 트레일 " + suffix);
        routeId = createRoute(trailId, "롤백 테스트 코스 " + suffix, List.of("WALK", "RUN", "BIKE"));
        startPointId = createStartPoint(routeId);

        when(currentMemberProvider.requireMemberId()).thenReturn(memberId);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM activities WHERE member_id = ?", memberId);
        for (Long id : routeIds) {
            favoriteRepository.findByMemberIdAndRouteId(memberId, id).ifPresent(favoriteRepository::delete);
            jdbcTemplate.update("DELETE FROM route_activity_types WHERE route_id = ?", id);
            jdbcTemplate.update("DELETE FROM route_start_points WHERE route_id = ?", id);
            jdbcTemplate.update("DELETE FROM routes WHERE id = ?", id);
        }
        jdbcTemplate.update("DELETE FROM trails WHERE id = ?", trailId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("ACT-01-E03 저장 도중 실패하면 이미 저장한 활동과 즐겨찾기가 둘 다 롤백된다")
    void E03_failureRollsBackActivityAndFavorite() throws Exception {
        // 즐겨찾기·활동이 모두 저장된 뒤의 마지막 단계(배지 지급)에서 실패하게 한다
        doThrow(new IllegalStateException("배지 지급 실패 (테스트)"))
                .when(badgeService).awardEarnedBadges(any(Member.class));

        Map<String, Object> payload = validPayload(routeId, startPointId);
        payload.put("saveAsFavorite", true);

        MvcResult result = postActivity(UUID.randomUUID().toString(), payload);

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(countActivities()).isZero();
        assertThat(favoriteRepository.findByMemberIdAndRouteId(memberId, routeId)).isEmpty();
    }

    @Test
    @DisplayName("ACT-01-X07 (추가) 코스가 지원하지 않는 활동 유형은 400이고 활동·즐겨찾기가 저장되지 않는다")
    void X07_unsupportedActivityTypeSavesNothing() throws Exception {
        Long walkOnlyRouteId = createRoute(trailId, "WALK 전용 코스", List.of("WALK"));
        Long walkOnlyStartPointId = createStartPoint(walkOnlyRouteId);

        Map<String, Object> payload = validPayload(walkOnlyRouteId, walkOnlyStartPointId);
        payload.put("activityType", "BIKE");
        payload.put("saveAsFavorite", true);

        MvcResult result = postActivity(UUID.randomUUID().toString(), payload);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(countActivities()).isZero();
        assertThat(favoriteRepository.findByMemberIdAndRouteId(memberId, walkOnlyRouteId)).isEmpty();
    }

    @Test
    @DisplayName("ACT-01-X08 (추가) 같은 Idempotency-Key 로 동시에 요청해도 모두 같은 결과를 받고 활동은 1건만 저장된다")
    void X08_concurrentSameKey() throws Exception {
        int threads = 5;
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(validPayload(routeId, startPointId));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/activities")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        Set<Long> activityIds = new HashSet<>();
        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get(20, TimeUnit.SECONDS);
            statuses.add(result.getResponse().getStatus());
            if (result.getResponse().getStatus() == 201) {
                activityIds.add(readActivityId(result));
            }
        }
        pool.shutdown();

        // 유니크 제약 위반·데드락이 500 으로 새지 않고 전부 같은 결과(201, 같은 activityId)여야 한다
        assertThat(statuses).containsOnly(201);
        assertThat(activityIds).hasSize(1);

        Integer saved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE member_id = ? AND idempotency_key = ?",
                Integer.class, memberId, key);
        assertThat(saved).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // 헬퍼
    // ---------------------------------------------------------------------

    private int countActivities() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE member_id = ?", Integer.class, memberId);
        return count == null ? 0 : count;
    }

    private MvcResult postActivity(String idempotencyKey, Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post("/api/v1/activities")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();
    }

    private long readActivityId(MvcResult result) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("activityId")
                .asLong();
    }

    private Map<String, Object> validPayload(Long routeId, Long startPointId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routeId", routeId);
        payload.put("startPointId", startPointId);
        payload.put("activityType", "WALK");
        payload.put("startedAt", "2026-11-12T09:00:00+09:00");
        payload.put("endedAt", "2026-11-12T09:30:00+09:00");
        payload.put("elapsedSeconds", 1800);
        payload.put("distanceMeters", "2500.00");
        payload.put("trackPoints", List.of(
                point(37.5665, 126.9780, "2026-11-12T09:00:00+09:00"),
                point(37.5670, 126.9790, "2026-11-12T09:30:00+09:00")
        ));
        payload.put("saveAsFavorite", false);
        payload.put("simulated", false);
        return payload;
    }

    private static Map<String, Object> point(double latitude, double longitude, String recordedAt) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("latitude", latitude);
        point.put("longitude", longitude);
        point.put("recordedAt", recordedAt);
        return point;
    }

    private Long createTrail(String name) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return insert("""
                        INSERT INTO trails
                            (name, center_latitude, center_longitude, length_meters,
                             geometry_geojson, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                name, 37.5665, 126.9780, 5000.00,
                "{\"type\":\"LineString\",\"coordinates\":[[126.9780,37.5665],[126.9790,37.5670]]}",
                now, now);
    }

    private Long createRoute(Long trailId, String name, List<String> activityTypes) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Long id = insert("""
                        INSERT INTO routes
                            (trail_id, name, distance_meters, estimated_duration_seconds,
                             geometry_geojson, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                trailId, name, 2500.00, 1800,
                "{\"type\":\"LineString\",\"coordinates\":[[126.9780,37.5665],[126.9790,37.5670]]}",
                now, now);
        routeIds.add(id);
        for (String type : activityTypes) {
            jdbcTemplate.update(
                    "INSERT INTO route_activity_types (route_id, activity_type) VALUES (?, ?)", id, type);
        }
        return id;
    }

    private Long createStartPoint(Long routeId) {
        return insert("""
                        INSERT INTO route_start_points (route_id, name, latitude, longitude)
                        VALUES (?, ?, ?, ?)
                        """,
                routeId, "테스트 출발점", 37.5665, 126.9780);
    }

    private Long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}