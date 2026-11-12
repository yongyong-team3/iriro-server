package com.iriro.server.activity.presentation;

import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.domain.BadgeCode;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:activity-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ActivityPersistenceApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired ActivityRepository activities;
    @Autowired FavoriteRepository favorites;
    @Autowired BadgeAwardRepository badgeAwards;
    @Autowired JwtTokenService jwt;

    @Test
    void savesFavoriteAndReturnsSameResultOnRetry() throws Exception {
        Member member = members.saveAndFlush(new Member("Runner", "runner"));
        long[] ids = routeAndStartPoint();
        String key = UUID.randomUUID().toString();
        String body = body(ids, "false", true);
        String token = jwt.issueAccessToken(member.getId()).value();

        mockMvc.perform(post("/api/v1/activities").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.favoriteAdded").value(true));
        mockMvc.perform(post("/api/v1/activities").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.favoriteAdded").value(true));

        assertThat(activities.countByMemberIdAndSimulatedFalse(member.getId())).isEqualTo(1);
        assertThat(favorites.findByMemberIdAndRouteId(member.getId(), ids[0])).isPresent();
        assertThat(activities.findByMemberIdAndIdempotencyKey(member.getId(), key)).isPresent();
    }

    @Test
    void simulatedActivityPersistsButDoesNotCount() throws Exception {
        Member member = members.saveAndFlush(new Member("Walker", "walker"));
        long[] ids = routeAndStartPoint();
        String token = jwt.issueAccessToken(member.getId()).value();
        mockMvc.perform(post("/api/v1/activities").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                        .content(body(ids, "true", false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.simulated").value(true));

        assertThat(activities.countByMemberIdAndSimulatedFalse(member.getId())).isZero();
        assertThat(activities.findAll().stream().filter(a -> a.getMember().getId().equals(member.getId())).toList())
                .hasSize(1).allSatisfy(a -> assertThat(a.isSimulated()).isTrue());
    }

    @Test
    void twentiethRealWalkAwardsBadgeDuringSave() throws Exception {
        Member member = members.saveAndFlush(new Member("Twenty", "twenty"));
        long[] ids = routeAndStartPoint();
        for (int i = 0; i < 19; i++) {
            jdbc.update("""
                    INSERT INTO activities (member_id,route_id,start_point_id,activity_type,started_at,ended_at,
                    elapsed_seconds,distance_meters,track_points_json,simulated,favorite_added,idempotency_key,
                    request_hash,created_at)
                    VALUES (?,?,?,'WALK',CURRENT_TIMESTAMP,DATEADD('MINUTE',1,CURRENT_TIMESTAMP),
                    60,100,'[]',FALSE,FALSE,?,'seed',CURRENT_TIMESTAMP)
                    """, member.getId(), ids[0], ids[1], "walk-" + i);
        }
        String token = jwt.issueAccessToken(member.getId()).value();
        mockMvc.perform(post("/api/v1/activities").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                        .content(body(ids, "false", false)))
                .andExpect(status().isCreated());

        assertThat(badgeAwards.findByMemberId(member.getId()))
                .extracting(a -> a.getBadgeCode()).containsExactly(BadgeCode.WALK_LOVER);
    }

    @Test
    void listFiltersOwnRecordsAndIncludesSimulationFlag() throws Exception {
        Member member = members.saveAndFlush(new Member("Lister", "lister"));
        Member other = members.saveAndFlush(new Member("Other", "other"));
        long[] ids = routeAndStartPoint();
        insertActivity(member.getId(), ids, "2026-11-09T01:00:00", "WALK", false, 2400);
        insertActivity(member.getId(), ids, "2026-11-10T01:00:00", "WALK", true, 2000);
        insertActivity(member.getId(), ids, "2026-10-09T01:00:00", "WALK", false, 1000);
        insertActivity(other.getId(), ids, "2026-11-11T01:00:00", "WALK", false, 3000);

        mockMvc.perform(get("/api/v1/activities").header("Authorization", bearer(member))
                        .param("month", "2026-11").param("activityType", "WALK")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.content[0].simulated").value(true))
                .andExpect(jsonPath("$.data.content[0].routeName").value("코스"));
    }

    @Test
    void detailReturnsPaceAndGeoJsonButHidesOtherMembersRecord() throws Exception {
        Member member = members.saveAndFlush(new Member("Detail", "detail"));
        Member other = members.saveAndFlush(new Member("Alien", "alien"));
        long[] ids = routeAndStartPoint();
        long activityId = insertActivity(member.getId(), ids, "2026-11-09T01:00:00", "WALK", false, 2400);
        long otherId = insertActivity(other.getId(), ids, "2026-11-09T02:00:00", "WALK", false, 1000);

        mockMvc.perform(get("/api/v1/activities/{id}", activityId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averagePaceSecondsPerKm").value(750))
                .andExpect(jsonPath("$.data.geometry.coordinates[0][0]").value(127.2355))
                .andExpect(jsonPath("$.data.geometry.coordinates[0][1]").value(37.3245));
        mockMvc.perform(get("/api/v1/activities/{id}", otherId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    void weeklyUsesKoreanMondayBoundaryAndExcludesSimulation() throws Exception {
        Member member = members.saveAndFlush(new Member("Weekly", "weekly"));
        long[] ids = routeAndStartPoint();
        insertActivity(member.getId(), ids, "2026-11-08T14:30:00", "WALK", false, 1000);
        insertActivity(member.getId(), ids, "2026-11-08T15:30:00", "WALK", false, 2400);
        insertActivity(member.getId(), ids, "2026-11-09T01:00:00", "WALK", true, 2000);

        mockMvc.perform(get("/api/v1/activities/stats/weekly")
                        .header("Authorization", bearer(member)).param("date", "2026-11-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStartDate").value("2026-11-09"))
                .andExpect(jsonPath("$.data.activityCount").value(1))
                .andExpect(jsonPath("$.data.distanceMeters").value(2400))
                .andExpect(jsonPath("$.data.daily[0].activityCount").value(1))
                .andExpect(jsonPath("$.data.daily[6].activityCount").value(0))
                .andExpect(jsonPath("$.data.recentActivities.length()").value(1));
    }

    @Test
    void monthlyUsesKoreanDatesAndStreakAcrossMonthBoundary() throws Exception {
        Member member = members.saveAndFlush(new Member("Monthly", "monthly"));
        long[] ids = routeAndStartPoint();
        insertActivity(member.getId(), ids, "2026-10-31T14:30:00", "WALK", false, 1000); // 10/31 23:30 KST
        insertActivity(member.getId(), ids, "2026-10-31T15:30:00", "WALK", false, 2000); // 11/01 00:30 KST
        insertActivity(member.getId(), ids, "2026-11-01T15:30:00", "WALK", false, 3000); // 11/02 00:30 KST
        insertActivity(member.getId(), ids, "2026-11-02T15:30:00", "WALK", true, 4000);

        mockMvc.perform(get("/api/v1/activities/stats/monthly")
                        .header("Authorization", bearer(member)).param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityCount").value(2))
                .andExpect(jsonPath("$.data.distanceMeters").value(5000))
                .andExpect(jsonPath("$.data.activityDates[0]").value("2026-11-01"))
                .andExpect(jsonPath("$.data.activityDates[1]").value("2026-11-02"))
                .andExpect(jsonPath("$.data.longestStreakDays").value(3));
    }

    @Test
    void monthlyCurrentStreakIncludesYesterdayWhenTodayHasNoActivity() throws Exception {
        Member member = members.saveAndFlush(new Member("Streak", "streak"));
        long[] ids = routeAndStartPoint();
        java.time.LocalDate yesterday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1);
        java.time.LocalDate before = yesterday.minusDays(1);
        insertActivity(member.getId(), ids, koreanDayAsUtc(before), "WALK", false, 1000);
        insertActivity(member.getId(), ids, koreanDayAsUtc(yesterday), "WALK", false, 1000);

        mockMvc.perform(get("/api/v1/activities/stats/monthly")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreakDays").value(2));
    }

    @Test
    void readEndpointsRejectInvalidFiltersAndRequireAuthentication() throws Exception {
        Member member = members.saveAndFlush(new Member("Filters", "filters"));
        String token = bearer(member);
        mockMvc.perform(get("/api/v1/activities").header("Authorization", token).param("month", "2026-13"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/activities").header("Authorization", token).param("activityType", "SWIM"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/activities").header("Authorization", token).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/activities/stats/weekly").header("Authorization", token).param("date", "2026-02-30"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/activities/stats/monthly").header("Authorization", token).param("month", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/activities"))
                .andExpect(status().isUnauthorized());
    }

    private long[] routeAndStartPoint() {
        jdbc.update("INSERT INTO trails (name, center_latitude, center_longitude, length_meters, geometry_geojson, created_at, updated_at) VALUES ('하천',37.3,127.2,1000,'{\"type\":\"LineString\",\"coordinates\":[]}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        Long trailId = jdbc.queryForObject("SELECT MAX(id) FROM trails", Long.class);
        jdbc.update("INSERT INTO routes (trail_id,name,distance_meters,estimated_duration_seconds,geometry_geojson,created_at,updated_at) VALUES (?,'코스',1000,600,'{\"type\":\"LineString\",\"coordinates\":[]}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", trailId);
        Long routeId = jdbc.queryForObject("SELECT MAX(id) FROM routes", Long.class);
        jdbc.update("INSERT INTO route_activity_types (route_id, activity_type) VALUES (?, 'WALK')", routeId);
        jdbc.update("INSERT INTO route_start_points (route_id,name,latitude,longitude) VALUES (?,'출발',37.3,127.2)", routeId);
        Long startId = jdbc.queryForObject("SELECT MAX(id) FROM route_start_points", Long.class);
        return new long[]{routeId, startId};
    }

    private long insertActivity(long memberId, long[] ids, String startedUtc, String type,
                                boolean simulated, long meters) {
        jdbc.update("""
                INSERT INTO activities (member_id,route_id,start_point_id,activity_type,started_at,ended_at,
                elapsed_seconds,distance_meters,track_points_json,simulated,favorite_added,idempotency_key,
                request_hash,created_at)
                VALUES (?,?,?,?,?,DATEADD('SECOND',1800,CAST(? AS TIMESTAMP)),1800,?, ?, ?,FALSE,?,'seed',CURRENT_TIMESTAMP)
                """, memberId, ids[0], ids[1], type, startedUtc, startedUtc, meters,
                "[{\"latitude\":37.3245,\"longitude\":127.2355,\"recordedAt\":\"2026-11-09T10:00:00+09:00\"},"
                        + "{\"latitude\":37.325,\"longitude\":127.236,\"recordedAt\":\"2026-11-09T10:30:00+09:00\"}]",
                simulated, UUID.randomUUID().toString());
        return jdbc.queryForObject("SELECT MAX(id) FROM activities", Long.class);
    }

    private String bearer(Member member) {
        return "Bearer " + jwt.issueAccessToken(member.getId()).value();
    }

    private String koreanDayAsUtc(java.time.LocalDate day) {
        return day.atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime().toString();
    }

    private String body(long[] ids, String simulated, boolean favorite) {
        return """
                {"routeId":%d,"startPointId":%d,"activityType":"WALK",
                "startedAt":"2026-11-09T10:00:00+09:00","endedAt":"2026-11-09T10:30:00+09:00",
                "elapsedSeconds":1800,"distanceMeters":2400,
                "trackPoints":[{"latitude":37.3245,"longitude":127.2355,"recordedAt":"2026-11-09T10:00:00+09:00"},
                {"latitude":37.325,"longitude":127.236,"recordedAt":"2026-11-09T10:30:00+09:00"}],
                "saveAsFavorite":%s,"simulated":%s}
                """.formatted(ids[0], ids[1], favorite, simulated);
    }
}
