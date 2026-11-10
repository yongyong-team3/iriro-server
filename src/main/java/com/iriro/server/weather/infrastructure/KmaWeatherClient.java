
package com.iriro.server.weather.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KmaWeatherClient {

    @Value("${kma.weather.url}")
    private String baseUrl;

    @Value("${kma.weather.service-key}")
    private String serviceKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 기상청 초단기실황 / 초단기예보 데이터 통합 조회
     */
    public Map<String, Integer> fetchWeatherData(int nx, int ny) {

        LocalDateTime now = LocalDateTime.now();

        Map<String, Integer> result = new HashMap<>();

        try {
            // 1. 초단기실황 기준 시간 계산
            LocalDateTime ncstBaseTime =
                    now.getMinute() < 40
                            ? now.minusHours(1)
                            : now;

            String ncstBaseDate = ncstBaseTime.format(DATE_FORMAT);
            String ncstBaseTimeStr =
                    ncstBaseTime.format(DateTimeFormatter.ofPattern("HH00"));

            // 2. 초단기실황 API 호출
            URI ncstUri = UriComponentsBuilder
                    .fromUriString(baseUrl + "/getUltraSrtNcst")
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", 10)
                    .queryParam("dataType", "JSON")
                    .queryParam("base_date", ncstBaseDate)
                    .queryParam("base_time", ncstBaseTimeStr)
                    .queryParam("nx", nx)
                    .queryParam("ny", ny)
                    .build(true)
                    .toUri();

            JsonNode ncstResponse =
                    restTemplate.getForObject(ncstUri, JsonNode.class);

            parseNcstItems(ncstResponse, result);

            // 3. 초단기예보 기준 시간 계산
            // 초단기예보는 매시간 30분에 발표됨
            LocalDateTime fcstBaseTime =
                    now.getMinute() < 45
                            ? now.minusHours(1)
                            : now;

            String fcstBaseDate = fcstBaseTime.format(DATE_FORMAT);
            String fcstBaseTimeStr =
                    fcstBaseTime.format(DateTimeFormatter.ofPattern("HH00"));

            // 4. 초단기예보 API 호출
            URI fcstUri = UriComponentsBuilder
                    .fromUriString(baseUrl + "/getUltraSrtFcst")
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", 1)
                    .queryParam("numOfRows", 60)
                    .queryParam("dataType", "JSON")
                    .queryParam("base_date", fcstBaseDate)
                    .queryParam("base_time", fcstBaseTimeStr)
                    .queryParam("nx", nx)
                    .queryParam("ny", ny)
                    .build(true)
                    .toUri();

            JsonNode fcstResponse =
                    restTemplate.getForObject(fcstUri, JsonNode.class);

            parseFcstItems(fcstResponse, result);

        } catch (Exception e) {
            System.err.println("기상청 API 호출 실패: " + e.getMessage());
        }

        return result;
    }

    /**
     * 초단기실황 데이터 파싱
     */
    private void parseNcstItems(
            JsonNode response,
            Map<String, Integer> resultMap
    ) {

        JsonNode items = response
                .path("response")
                .path("body")
                .path("items")
                .path("item");

        for (JsonNode item : items) {

            String category = item.path("category").asText();

            if ("T1H".equals(category)) {
                // 기온
                resultMap.put(
                        "T1H",
                        (int) Math.round(item.path("obsrValue").asDouble())
                );

            } else if ("PTY".equals(category)) {
                // 강수형태
                resultMap.put(
                        "PTY",
                        item.path("obsrValue").asInt()
                );
            }
        }
    }

    /**
     * 초단기예보 데이터 파싱
     */
    private void parseFcstItems(
            JsonNode response,
            Map<String, Integer> resultMap
    ) {

        JsonNode items = response
                .path("response")
                .path("body")
                .path("items")
                .path("item");

        for (JsonNode item : items) {

            String category = item.path("category").asText();

            if ("SKY".equals(category)
                    && !resultMap.containsKey("SKY")) {

                // 하늘상태
                resultMap.put(
                        "SKY",
                        item.path("fcstValue").asInt()
                );
            }
        }
    }
}
