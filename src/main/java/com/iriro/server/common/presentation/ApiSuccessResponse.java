package com.iriro.server.common.presentation;

public record ApiSuccessResponse<T>(String code, String message, T data) {

    public static <T> ApiSuccessResponse<T> of(String code, String message, T data) {
        return new ApiSuccessResponse<>(code, message, data);
    }
}
