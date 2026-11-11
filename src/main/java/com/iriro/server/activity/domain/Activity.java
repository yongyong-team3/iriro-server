package com.iriro.server.activity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "activities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_activity_idempotency",
                        columnNames = {"member_id", "idempotency_key"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 회원 정보
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 경로 ID
    @Column(name = "route_id", nullable = false)
    private Long routeId;

    // 출발 지점 ID
    @Column(name = "start_point_id", nullable = false)
    private Long startPointId;

    // 우회 경로 ID
    @Column(name = "detour_id")
    private Long detourId;

    // 운동 종류
    @Column(name = "activity_type", nullable = false, length = 12)
    private String activityType;

    // 운동 시작 시간
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    // 운동 종료 시간
    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    // 운동 소요 시간 (초)
    @Column(name = "elapsed_seconds", nullable = false)
    private Integer elapsedSeconds;

    // 이동 거리 (m 단위, DB: decimal(10,2))
    @Column(name = "distance_meters", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceMeters;

    // GPS 이동 경로 JSON
    @Lob
    @Column(name = "track_points_json", nullable = false, columnDefinition = "LONGTEXT")
    private String trackPointsJson;

    // 시연 기록은 보관하지만 실제 활동 실적에는 합산하지 않는다.
    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    // 중복 요청 방지 키
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    // 요청 데이터 해시
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    // 생성 시간
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
}