package com.iriro.server.favorite.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "GeoJSON LineString, 좌표 순서 [경도, 위도]")
public record FavoriteGeometry(
        @Schema(example = "LineString") String type,
        List<List<BigDecimal>> coordinates
) {
}
