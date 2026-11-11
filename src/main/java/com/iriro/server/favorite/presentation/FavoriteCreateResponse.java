package com.iriro.server.favorite.presentation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FavoriteCreateResponse {

    private Long favoriteId;
    private Long routeId;

    public static FavoriteCreateResponse of(
            Long favoriteId,
            Long routeId
    ) {
        return new FavoriteCreateResponse(favoriteId, routeId);
    }
}