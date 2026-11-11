package com.iriro.server.favorite.application;

import com.iriro.server.favorite.presentation.FavoriteCreateResponse;

public record FavoriteAddResult(FavoriteCreateResponse response, boolean created) {
}