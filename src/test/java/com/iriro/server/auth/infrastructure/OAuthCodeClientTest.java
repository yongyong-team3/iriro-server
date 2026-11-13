package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.common.application.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthCodeClientTest {
    @Test
    void usesServerSideFormPostForBothProvidersAndSendsNaverState() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        Map<String, String> requests = new ConcurrentHashMap<>();
        server.createContext("/kakao/token", exchange -> {
            requests.put("kakao-method", exchange.getRequestMethod());
            requests.put("kakao-uri", exchange.getRequestURI().toString());
            requests.put("kakao-body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"access_token\":\"kakao-access\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/naver/token", exchange -> {
            requests.put("naver-method", exchange.getRequestMethod());
            requests.put("naver-uri", exchange.getRequestURI().toString());
            requests.put("naver-body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"access_token\":\"naver-access\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            OAuthCodeClient client = new OAuthCodeClient(new ObjectMapper(), "kakao-key", "kakao-secret",
                    "naver-id", "naver-secret", base + "/kakao/authorize", base + "/kakao/token",
                    base + "/naver/authorize", base + "/naver/token");
            String callback = "https://api.example.test/api/v1/auth/oauth/kakao/callback";
            assertThat(parameters(client.authorizationUri(SocialProvider.KAKAO, "state-1", callback).getRawQuery()))
                    .containsEntry("client_id", "kakao-key")
                    .containsEntry("redirect_uri", callback)
                    .containsEntry("state", "state-1");
            assertThat(client.exchange(SocialProvider.KAKAO, "code-1", "state-1", callback))
                    .isEqualTo("kakao-access");
            assertThat(client.exchange(SocialProvider.NAVER, "code-2", "state-2",
                    "https://api.example.test/api/v1/auth/oauth/naver/callback"))
                    .isEqualTo("naver-access");
            assertThat(requests.get("kakao-method")).isEqualTo("POST");
            assertThat(requests.get("naver-method")).isEqualTo("POST");
            assertThat(requests.get("kakao-uri")).doesNotContain("secret", "code-1");
            assertThat(requests.get("naver-uri")).doesNotContain("secret", "code-2");
            assertThat(parameters(requests.get("kakao-body")))
                    .containsEntry("grant_type", "authorization_code")
                    .containsEntry("client_secret", "kakao-secret")
                    .containsEntry("code", "code-1");
            assertThat(parameters(requests.get("naver-body")))
                    .containsEntry("grant_type", "authorization_code")
                    .containsEntry("client_secret", "naver-secret")
                    .containsEntry("state", "state-2")
                    .containsEntry("code", "code-2");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingProviderKeysAreUnavailable() {
        OAuthCodeClient client = new OAuthCodeClient(new ObjectMapper(), "", "", "", "",
                "https://kauth.kakao.com/oauth/authorize", "https://kauth.kakao.com/oauth/token",
                "https://nid.naver.com/oauth2.0/authorize", "https://nid.naver.com/oauth2.0/token");
        assertThatThrownBy(() -> client.authorizationUri(SocialProvider.KAKAO, "state", "https://api.example.test/callback"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("OAUTH_NOT_CONFIGURED");
        assertThatThrownBy(() -> client.authorizationUri(SocialProvider.NAVER, "state", "https://api.example.test/callback"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("OAUTH_NOT_CONFIGURED");
    }

    private Map<String, String> parameters(String form) {
        Map<String, String> result = new ConcurrentHashMap<>();
        Arrays.stream(form.split("&")).map(entry -> entry.split("=", 2)).forEach(entry ->
                result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(entry[1], StandardCharsets.UTF_8)));
        return result;
    }
}
