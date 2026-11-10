package com.iriro.server.common.presentation;

import java.util.List;

public record ApiErrorResponse(String code, String message, List<FieldError> errors) {

    public ApiErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public record FieldError(String field, String reason) {
    }
}
