package com.iriro.server.auth.presentation;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(@NotBlank String loginCode) { }
