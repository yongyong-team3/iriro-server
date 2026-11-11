package com.iriro.server.activity.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "활동 완료 기록")
public record ActivitySaveRequest(
        @NotNull @Positive Long routeId,
        @NotNull @Positive Long startPointId,
        @NotBlank @Pattern(regexp = "WALK|RUN|BIKE") String activityType,
        @NotNull OffsetDateTime startedAt,
        @NotNull OffsetDateTime endedAt,
        @NotNull @Positive Integer elapsedSeconds,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal distanceMeters,
        @NotNull @Size(min = 2) List<@NotNull @Valid TrackPoint> trackPoints,
        Boolean saveAsFavorite,
        @Schema(defaultValue = "false") Boolean simulated
) {
    public boolean isSimulated() { return Boolean.TRUE.equals(simulated); }
    public boolean shouldSaveFavorite() { return Boolean.TRUE.equals(saveAsFavorite); }

    public record TrackPoint(
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotNull OffsetDateTime recordedAt
    ) {}
}
