package com.iriro.server.favorite.presentation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FavoriteListResponse {

    private List<FavoriteResponse> favorites;
}