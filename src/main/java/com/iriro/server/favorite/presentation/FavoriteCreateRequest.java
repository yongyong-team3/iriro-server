package com.iriro.server.favorite.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FavoriteCreateRequest {

    @NotNull
    @Positive
    private Long routeId;
}