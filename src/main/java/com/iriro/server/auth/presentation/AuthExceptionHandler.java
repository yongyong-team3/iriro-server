package com.iriro.server.auth.presentation;

import com.iriro.server.common.application.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {EmailAuthController.class, SocialAuthController.class,
        OAuthFlowController.class})
public class AuthExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<AuthErrorResponse> apiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new AuthErrorResponse(exception.getCode(), exception.getMessage(), null));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<AuthErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse("INVALID_REQUEST", "요청 정보를 확인해 주세요.", null));
    }

    public record AuthErrorResponse(String code, String message, Object errors) { }
}
