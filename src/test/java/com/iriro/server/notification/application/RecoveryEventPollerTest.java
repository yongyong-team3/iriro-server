package com.iriro.server.notification.application;

import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.notification.domain.NotificationSetting;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import com.iriro.server.notification.infrastructure.NotificationSettingRepository;
import com.iriro.server.notification.infrastructure.RecoveryEventRepository;
import com.iriro.server.trail.application.TrailStatusService;
import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.TrailRepository;
import com.iriro.server.trail.presentation.TrailStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:recovery-event-poller;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class RecoveryEventPollerTest {
    @Autowired RecoveryEventPoller poller;
    @Autowired MemberRepository members;
    @Autowired NotificationSettingRepository settings;
    @Autowired NotificationRepository notifications;
    @Autowired RecoveryEventRepository events;
    @Autowired TrailRepository trails;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TrailStatusService statuses;

    @Test
    void pollsLiveFirstForecastOnceAndStartsNewEventOnlyAfterLiveNormal() {
        long trailId = insertTrail();
        Member member = members.saveAndFlush(new Member(null, null));
        settings.saveAndFlush(new NotificationSetting(member, true));
        OffsetDateTime first = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);

        stub(trailId, status(trailId, "REPLAY", "OK", "HIGH_WATER", first, "PREDICTED", 240));
        poller.poll();
        assertThat(events.count()).isZero();

        stub(trailId, status(trailId, "LIVE", "MISSING", "UNKNOWN", first, "PREDICTED", 240));
        poller.poll();
        assertThat(events.count()).isZero();

        stub(trailId, status(trailId, "LIVE", "OK", "HIGH_WATER", first, "PREDICTED", 240));
        poller.poll();
        assertThat(events.count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
        poller.poll();
        assertThat(notifications.count()).isEqualTo(1);

        stub(trailId, status(trailId, "REPLAY", "OK", "NORMAL", first.plusMinutes(1), null, null));
        poller.poll();
        assertThat(events.findAll().getFirst().getEndedAt()).isNull();

        stub(trailId, status(trailId, "LIVE", "OK", "NORMAL", first.plusMinutes(2), null, null));
        poller.poll();
        assertThat(events.findAll().getFirst().getEndedAt()).isNotNull();

        stub(trailId, status(trailId, "LIVE", "OK", "HIGH_WATER", first.plusMinutes(3), "PREDICTED", 120));
        poller.poll();
        assertThat(events.count()).isEqualTo(2);
        assertThat(notifications.count()).isEqualTo(2);
    }

    private void stub(long trailId, TrailStatusResponse response) {
        when(statuses.status(any(Trail.class))).thenReturn(response);
    }

    private TrailStatusResponse status(long trailId, String source, String quality, String water,
                                       OffsetDateTime observedAt, String recovery, Integer etaMinutes) {
        return new TrailStatusResponse(trailId, "월촌교", source, quality, null, null, water,
                "NORMAL".equals(water) ? new BigDecimal("1.0") : new BigDecimal("1.8"),
                observedAt, null, null, null, recovery, etaMinutes, null, null, observedAt);
    }

    private long insertTrail() {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement("""
                INSERT INTO trails (name, center_latitude, center_longitude, length_meters,
                    geometry_geojson, water_station_code, created_at, updated_at)
                VALUES ('월촌교', 37.324500, 127.235500, 2400,
                    '{"type":"LineString","coordinates":[[127.2355,37.3245],[127.2360,37.3250]]}',
                    '1016607', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, java.sql.Statement.RETURN_GENERATED_KEYS), keys);
        return keys.getKey().longValue();
    }
}
