package com.iriro.server.auth.presentation;

public record AuthResponse(Long memberId, String email, String nickname) {
}
