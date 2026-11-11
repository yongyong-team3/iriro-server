package com.iriro.server.activity.presentation;

public record ActivitySaveResponse(Long activityId, boolean favoriteAdded, boolean simulated) {
}
