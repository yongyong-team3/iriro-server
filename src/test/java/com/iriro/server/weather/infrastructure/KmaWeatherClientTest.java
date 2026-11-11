package com.iriro.server.weather.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KmaWeatherClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void readsTemperatureObservationTimeFromKmaResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/getUltraSrtNcst", exchange -> respond(exchange, """
                {"response":{"body":{"items":{"item":[
                  {"category":"T1H","obsrValue":"17.4","baseDate":"20261111","baseTime":"1200"},
                  {"category":"PTY","obsrValue":"0"}
                ]}}}}
                """));
        server.createContext("/getUltraSrtFcst", exchange -> respond(exchange, """
                {"response":{"body":{"items":{"item":[{"category":"SKY","fcstValue":"3"}]}}}}
                """));
        server.start();

        KmaWeatherClient client = new KmaWeatherClient();
        ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(client, "serviceKey", "test-key");

        var weather = client.fetchWeatherData(60, 127);
        assertThat(weather).containsEntry("T1H", 17)
                .containsEntry("PTY", 0)
                .containsEntry("SKY", 3)
                .containsEntry("OBSERVED_DATE", 20261111)
                .containsEntry("OBSERVED_TIME", 1200);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
