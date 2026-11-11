package com.iriro.server.favorite.presentation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteCreateRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private FavoriteCreateRequest request(Long routeId) {
        FavoriteCreateRequest request = new FavoriteCreateRequest();
        ReflectionTestUtils.setField(request, "routeId", routeId);
        return request;
    }

    @Test
    @DisplayName("routeId 가 양수면 검증 통과")
    void valid() {
        Set<ConstraintViolation<FavoriteCreateRequest>> violations = validator.validate(request(1L));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("routeId 가 null 이면 위반")
    void nullRouteId() {
        Set<ConstraintViolation<FavoriteCreateRequest>> violations = validator.validate(request(null));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("routeId");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -100L})
    @DisplayName("routeId 가 0 이하이면 위반")
    void nonPositiveRouteId(long routeId) {
        Set<ConstraintViolation<FavoriteCreateRequest>> violations = validator.validate(request(routeId));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("routeId");
    }
}