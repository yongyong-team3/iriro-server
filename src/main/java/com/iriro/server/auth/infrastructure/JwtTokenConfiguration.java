package com.iriro.server.auth.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class JwtTokenConfiguration {

    @Bean
    JwtTokenService jwtTokenService(
            @Value("${iriro.auth.jwt-secret}") String secret,
            @Value("${iriro.auth.access-token-ttl-seconds}") long accessTtlSeconds,
            @Value("${iriro.auth.refresh-token-ttl-seconds}") long refreshTtlSeconds
    ) {
        return new JwtTokenService(secret, accessTtlSeconds, refreshTtlSeconds, Clock.systemUTC());
    }
}
