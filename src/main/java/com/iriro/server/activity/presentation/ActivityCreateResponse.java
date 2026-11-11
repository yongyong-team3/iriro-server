package com.iriro.server.activity.presentation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ActivityCreateResponse {

    private Long activityId;
    private Boolean favoriteAdded;
    private Boolean simulated;
}