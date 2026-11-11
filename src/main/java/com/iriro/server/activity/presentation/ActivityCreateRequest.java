package com.iriro.server.activity.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class ActivityCreateRequest {

        @NotNull
        @Positive
        private Long routeId;

        @NotNull
        @Positive
        private Long startPointId;

        @NotBlank
        @Size(max = 12)
        private String activityType;

        @NotNull
        private OffsetDateTime startedAt;

        @NotNull
        private OffsetDateTime endedAt;

        @NotNull
        @Positive
        private Integer elapsedSeconds;

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        private BigDecimal distanceMeters;

        @NotNull
        @Size(min = 2)
        private List<@Valid TrackPoint> trackPoints;

        private Boolean saveAsFavorite = false;

        private Boolean simulated = false;

        @JsonSetter("simulated")
        public void setSimulated(Boolean simulated) {
                if (simulated == null) {
                        throw new IllegalArgumentException(
                                "simulated는 null일 수 없습니다."
                        );
                }

                this.simulated = simulated;
        }

        @Getter
        @Setter
        @NoArgsConstructor
        public static class TrackPoint {

                @NotNull
                @DecimalMin("-90.0")
                @DecimalMax("90.0")
                private Double latitude;

                @NotNull
                @DecimalMin("-180.0")
                @DecimalMax("180.0")
                private Double longitude;

                @NotNull
                private OffsetDateTime recordedAt;
        }
}