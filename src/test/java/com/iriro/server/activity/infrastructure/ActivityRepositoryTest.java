package com.iriro.server.activity.infrastructure;

import com.iriro.server.activity.domain.Activity;
import com.iriro.server.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ActivityRepositoryTest.JpaTestConfiguration.class)
class ActivityRepositoryTest {

    @Autowired ActivityRepository activities;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM activities");
        jdbc.execute("DELETE FROM members");
        jdbc.update("INSERT INTO members (id, member_name, member_name_key, created_at, updated_at) VALUES (1, 'Aaa', 'aaa', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO members (id, member_name, member_name_key, created_at, updated_at) VALUES (2, 'Bbb', 'bbb', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    @Test
    void onlyRealActivitiesContributeToCountAndDistance() {
        insertActivity(1, 1, 1000, false);
        insertActivity(2, 1, 2000, true);
        insertActivity(3, 2, 5000, false);

        assertThat(activities.countByMemberIdAndSimulatedFalse(1L)).isEqualTo(1);
        assertThat(activities.sumRealDistanceByMemberId(1L)).isEqualTo(1000);
    }

    @Test
    void simulationOnlyMemberHasZeroRealProgress() {
        insertActivity(1, 1, 2000, true);

        assertThat(activities.countByMemberIdAndSimulatedFalse(1L)).isZero();
        assertThat(activities.sumRealDistanceByMemberId(1L)).isZero();
    }

    @Test
    void savesSimulationFlagWithActivity() {
        Member member = new Member("Aaa", "aaa");
        ReflectionTestUtils.setField(member, "id", 1L);
        Activity activity = Activity.builder()
                .memberId(1L)
                .routeId(1L)
                .startPointId(1L)
                .activityType("WALK")
                .startedAt(LocalDateTime.now().minusMinutes(1))
                .endedAt(LocalDateTime.now())
                .elapsedSeconds(60)
                .distanceMeters(BigDecimal.valueOf(2000))
                .trackPointsJson("[]")
                .simulated(true)
                .idempotencyKey("simulation")
                .requestHash("hash")
                .build();

        long id = activities.saveAndFlush(activity).getId();

        assertThat(activities.findById(id).orElseThrow().isSimulated()).isTrue();
        assertThat(activities.countByMemberIdAndSimulatedFalse(1L)).isZero();
    }

    private void insertActivity(long id, long memberId, long distance, boolean simulated) {
        jdbc.update("""
                INSERT INTO activities
                    (id, member_id, route_id, start_point_id, activity_type, started_at, ended_at,
                     elapsed_seconds, distance_meters, track_points_json, simulated,
                     idempotency_key, request_hash, created_at)
                VALUES (?, ?, 1, 1, 'WALK', CURRENT_TIMESTAMP, DATEADD('MINUTE', 1, CURRENT_TIMESTAMP),
                        60, ?, '[]', ?, ?, 'hash', CURRENT_TIMESTAMP)
                """, id, memberId, distance, simulated, "key-" + id);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ActivityRepository.class)
    static class JpaTestConfiguration {
        @Bean DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:activity-repository;MODE=MySQL;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE IF NOT EXISTS members (id BIGINT PRIMARY KEY, member_name VARCHAR(12), member_name_key VARCHAR(12), created_at TIMESTAMP, updated_at TIMESTAMP)");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS activities (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, member_id BIGINT NOT NULL, route_id BIGINT NOT NULL,
                        start_point_id BIGINT NOT NULL, detour_id BIGINT, activity_type VARCHAR(12) NOT NULL,
                        started_at TIMESTAMP NOT NULL, ended_at TIMESTAMP NOT NULL,
                        elapsed_seconds INT NOT NULL, distance_meters DECIMAL(10,2) NOT NULL,
                        track_points_json LONGTEXT NOT NULL, simulated BOOLEAN NOT NULL,
                        favorite_added BOOLEAN NOT NULL DEFAULT FALSE,
                        idempotency_key VARCHAR(64) NOT NULL, request_hash CHAR(64) NOT NULL,
                        created_at TIMESTAMP NOT NULL)
                    """);
            return ds;
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(Activity.class.getPackageName(), Member.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.dialect", "org.hibernate.dialect.H2Dialect"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
    }
}
