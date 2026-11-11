package com.iriro.server.favorite.presentation;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FavoriteResponse {

    private Long favoriteId;
    private Long routeId;
    private String routeName;
    private Long trailId;
    private String trailName;
    private BigDecimal distanceMeters;
    private String geometry;
}