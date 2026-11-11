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
