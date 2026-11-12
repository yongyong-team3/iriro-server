package com.iriro.server.activity.presentation;

import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.CustomException;
import com.iriro.server.common.ErrorCode;
import com.iriro.server.favorite.infrastructure.FavoriteRepository;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ACT-01 ~ ACT-05 활동 기록 API 테스트 (테스트 ID 는 명세 체크리스트와 동일)
 *
 * - 인증: CurrentMemberProvider 를 Mock 으로 대체 (미인증 = UNAUTHORIZED_USER 예외 → 401)
 * - 데이터: 활동은 Entity 를 직접 만들지 않고 POST /activities 로 생성한다.
 * - 클래스 전체가 @Transactional 이라 각 테스트 종료 시 롤백된다.
 * - ACT-01-E03(롤백 검증)은 트랜잭션 밖에서 실행해야 하므로 ActivityRollbackTest 에 있다.
 * - "추가" 표시가 붙은 테스트(ACT-xx-X..)는 명세에는 없지만 코드 분기상 확인할 가치가 있는 케이스다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ActivityScenarioTest {

    private static final String URL = "/api/v1/activities";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ActivityRepository activityRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean CurrentMemberProvider currentMemberProvider;

    private Long memberId;
    private Long otherMemberId;
    private Long trailId;
    private Long routeId;
    private Long otherRouteId;
    private Long startPointId;
    private Long otherRouteStartPointId;

    @BeforeEach
    void setUp() {
        // member_name 은 최대 12자
        memberId = createMember("act-me");
        otherMemberId = createMember("act-other");
        trailId = createTrail("활동 테스트 트레일");
        routeId = createRoute(trailId, "활동 테스트 코스 A");
        otherRouteId = createRoute(trailId, "활동 테스트 코스 B");
        startPointId = createStartPoint(routeId);
        otherRouteStartPointId = createStartPoint(otherRouteId);

        loginAs(memberId);
    }

    // =====================================================================
    // ACT-01 POST /api/v1/activities
    // =====================================================================
    @Nested
    @DisplayName("ACT-01 활동 기록 저장")
    class CreateActivity {

        // ---------- 성공 ----------

        @Test
        @DisplayName("ACT-01-S01 유효한 코스·출발점·GPS 2점 이상·양수 거리/시간으로 저장하면 201과 activityId를 받는다")
        void S01_created() throws Exception {
            long before = activityRepository.count();

            create(UUID.randomUUID().toString(), validPayload())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.activityId").isNumber())
                    .andExpect(jsonPath("$.data.favoriteAdded").value(false))
                    .andExpect(jsonPath("$.data.simulated").value(false));

            assertThat(activityRepository.count()).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("ACT-01-S02 saveAsFavorite=true 면 활동과 코스 즐겨찾기가 함께 저장된다")
        void S02_saveAsFavorite() throws Exception {
            long before = activityRepository.count();
            Map<String, Object> payload = validPayload();
            payload.put("saveAsFavorite", true);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.favoriteAdded").value(true));

            assertThat(activityRepository.count()).isEqualTo(before + 1);
            assertThat(favoriteRepository.findByMemberIdAndRouteId(memberId, routeId)).isPresent();
        }

        @Test
        @DisplayName("ACT-01-S03 동일 Idempotency-Key + 동일 본문 재시도는 기존 결과를 돌려주고 기록을 1개만 남긴다")
        void S03_idempotentReplay() throws Exception {
            String key = UUID.randomUUID().toString();
            Map<String, Object> payload = validPayload();
            long before = activityRepository.count();

            long first = readActivityId(create(key, payload).andExpect(status().isCreated()).andReturn());
            long second = readActivityId(create(key, payload).andExpect(status().isCreated()).andReturn());

            assertThat(second).isEqualTo(first);
            assertThat(activityRepository.count()).isEqualTo(before + 1);
        }

        // ---------- 실패 ----------

        @Test
        @DisplayName("ACT-01-F01 인증 누락은 401이다")
        void F01_unauthorized() throws Exception {
            unauthenticated();

            create(UUID.randomUUID().toString(), validPayload())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ACT-01-F01 없는 코스는 404이다")
        void F01_routeNotFound() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("routeId", 999_999_999L);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
        }

        @Test
        @DisplayName("ACT-01-F01 없는 출발점은 404이다")
        void F01_startPointNotFound() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("startPointId", 999_999_999L);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("START_POINT_NOT_FOUND"));
        }

        @Test
        @DisplayName("ACT-01-F01 다른 코스에 속한 출발점은 404이다")
        void F01_startPointOfOtherRoute() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("startPointId", otherRouteStartPointId);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("START_POINT_NOT_FOUND"));
        }

        @ParameterizedTest(name = "ACT-01-F02 {0} -> 400")
        @MethodSource("com.iriro.server.activity.presentation.ActivityScenarioTest#f02Payloads")
        void F02_invalidGpsOrNumbers(String caseName, Consumer<Map<String, Object>> mutator) throws Exception {
            Map<String, Object> payload = validPayload();
            mutator.accept(payload);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ACT-01-F03 Idempotency-Key 헤더가 없으면 400이다")
        void F03_missingKeyHeader() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validPayload())))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "ACT-01-F03 잘못된 Idempotency-Key [{0}] -> 400")
        @MethodSource("com.iriro.server.activity.presentation.ActivityScenarioTest#invalidKeys")
        void F03_invalidKey(String key) throws Exception {
            create(key, validPayload()).andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "ACT-01-F03 {0} -> 400")
        @MethodSource("com.iriro.server.activity.presentation.ActivityScenarioTest#f03Payloads")
        void F03_missingFieldOrInvalidType(String caseName, Consumer<Map<String, Object>> mutator) throws Exception {
            Map<String, Object> payload = validPayload();
            mutator.accept(payload);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ACT-01-F04 같은 키로 다른 본문을 보내면 409 IDEMPOTENCY_CONFLICT 이다")
        void F04_idempotencyConflict() throws Exception {
            String key = UUID.randomUUID().toString();
            create(key, validPayload()).andExpect(status().isCreated());

            Map<String, Object> changed = validPayload();
            changed.put("distanceMeters", "9999.00");

            create(key, changed)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        }

        // ---------- 경계·예외 ----------

        @Test
        @DisplayName("ACT-01-E01 GPS 점 정확히 2개, 최소 양수 거리(0.01)·시간(1초)을 저장한다")
        void E01_minimumValues() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("elapsedSeconds", 1);
            payload.put("distanceMeters", "0.01");
            payload.put("trackPoints", List.of(
                    point(37.5665, 126.9780, "2026-11-12T09:00:00+09:00"),
                    point(37.5670, 126.9790, "2026-11-12T09:00:01+09:00")
            ));

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.activityId").isNumber());
        }

        @Test
        @DisplayName("ACT-01-E02 즐겨찾기가 이미 있으면 favoriteAdded=false 이고 중복 생성되지 않는다")
        void E02_favoriteAlreadyExists() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("saveAsFavorite", true);

            long favoritesBefore = favoriteRepository.count();

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(jsonPath("$.data.favoriteAdded").value(true));
            create(UUID.randomUUID().toString(), payload)     // 다른 키, 같은 코스
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.favoriteAdded").value(false));

            assertThat(favoriteRepository.count()).isEqualTo(favoritesBefore + 1);
        }

        // E03 (활동 또는 즐겨찾기 저장 실패 시 둘 다 롤백) -> ActivityRollbackTest

        // ---------- 추가 ----------

        @Test
        @DisplayName("ACT-01-X01 (추가) simulated=true 로 저장하면 응답도 simulated=true 이다")
        void X01_simulated() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("simulated", true);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.simulated").value(true));
        }

        @Test
        @DisplayName("ACT-01-X02 (추가) 재전송 응답의 favoriteAdded 는 false 이다")
        void X02_replayFavoriteAddedFalse() throws Exception {
            String key = UUID.randomUUID().toString();
            Map<String, Object> payload = validPayload();
            payload.put("saveAsFavorite", true);

            create(key, payload).andExpect(jsonPath("$.data.favoriteAdded").value(true));
            create(key, payload)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.favoriteAdded").value(false));
        }

        @Test
        @DisplayName("ACT-01-X03 (추가) 다른 회원은 같은 Idempotency-Key 를 써도 별도 활동으로 저장된다")
        void X03_sameKeyDifferentMember() throws Exception {
            String key = UUID.randomUUID().toString();
            Map<String, Object> payload = validPayload();

            long mine = readActivityId(create(key, payload).andReturn());

            loginAs(otherMemberId);
            long others = readActivityId(create(key, payload).andExpect(status().isCreated()).andReturn());

            assertThat(others).isNotEqualTo(mine);
        }

        @Test
        @DisplayName("ACT-01-X04 (추가) 같은 키에 simulated 값만 달라도 충돌이다")
        void X04_conflictOnSimulated() throws Exception {
            String key = UUID.randomUUID().toString();
            create(key, validPayload()).andExpect(status().isCreated());

            Map<String, Object> changed = validPayload();
            changed.put("simulated", true);

            create(key, changed).andExpect(status().isConflict());
        }

        @ParameterizedTest(name = "ACT-01-X05 (추가) {0} -> 400")
        @MethodSource("com.iriro.server.activity.presentation.ActivityScenarioTest#extraInvalidPayloads")
        void X05_extraInvalidBody(String caseName, Consumer<Map<String, Object>> mutator) throws Exception {
            Map<String, Object> payload = validPayload();
            mutator.accept(payload);

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ACT-01-X06 (추가) 위경도 경계값(±90, ±180)과 같은 recordedAt 은 허용된다")
        void X06_boundaryCoordinatesAndSameTime() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("trackPoints", List.of(
                    point(90.0, 180.0, "2026-11-12T09:00:00+09:00"),
                    point(-90.0, -180.0, "2026-11-12T09:00:00+09:00")
            ));

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ACT-01-X07 (추가) 코스가 지원하지 않는 활동 유형은 500이 아니라 400이고 저장되지 않는다")
        void X07_unsupportedActivityTypeForRoute() throws Exception {
            // WALK 만 지원하는 코스에 BIKE 활동을 저장하려는 경우
            Long walkOnlyRouteId = createRoute(trailId, "WALK 전용 코스", List.of("WALK"));
            Long walkOnlyStartPointId = createStartPoint(walkOnlyRouteId);
            long before = activityRepository.count();

            Map<String, Object> payload = validPayload();
            payload.put("routeId", walkOnlyRouteId);
            payload.put("startPointId", walkOnlyStartPointId);
            payload.put("activityType", "BIKE");

            create(UUID.randomUUID().toString(), payload)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            assertThat(activityRepository.count()).isEqualTo(before);
        }
    }

    static Stream<String> invalidKeys() {
        return Stream.of(
                "not-a-uuid",
                "123456789012345678901234567890123456",    // 36자지만 UUID 형식 아님
                "123e4567-e89b-12d3-a456-42661417400"       // 35자
        );
    }

    /** F02: GPS 점 0개·1개, 거리·시간 0 이하 */
    static Stream<Arguments> f02Payloads() {
        return Stream.of(
                inv("trackPoints 0개", p -> p.put("trackPoints", List.of())),
                inv("trackPoints 1개", p -> p.put("trackPoints",
                        List.of(point(37.5665, 126.9780, "2026-11-12T09:00:00+09:00")))),
                inv("elapsedSeconds 0", p -> p.put("elapsedSeconds", 0)),
                inv("elapsedSeconds 음수", p -> p.put("elapsedSeconds", -1)),
                inv("distanceMeters 0", p -> p.put("distanceMeters", "0.00")),
                inv("distanceMeters 음수", p -> p.put("distanceMeters", "-1.00"))
        );
    }

    /** F03: 필수 필드 누락·잘못된 activityType */
    static Stream<Arguments> f03Payloads() {
        return Stream.of(
                inv("routeId 누락", p -> p.remove("routeId")),
                inv("startPointId 누락", p -> p.remove("startPointId")),
                inv("activityType 누락", p -> p.remove("activityType")),
                inv("startedAt 누락", p -> p.remove("startedAt")),
                inv("endedAt 누락", p -> p.remove("endedAt")),
                inv("elapsedSeconds 누락", p -> p.remove("elapsedSeconds")),
                inv("distanceMeters 누락", p -> p.remove("distanceMeters")),
                inv("trackPoints 누락", p -> p.remove("trackPoints")),
                inv("activityType 허용되지 않는 값", p -> p.put("activityType", "SWIM")),
                inv("activityType 소문자", p -> p.put("activityType", "walk"))
        );
    }

    /** 추가: 명세에 없지만 코드에 검증이 있는 항목 */
    static Stream<Arguments> extraInvalidPayloads() {
        return Stream.of(
                inv("startedAt == endedAt", p -> p.put("endedAt", p.get("startedAt"))),
                inv("startedAt > endedAt", p -> p.put("endedAt", "2026-11-12T08:00:00+09:00")),
                inv("distanceMeters 소수 3자리", p -> p.put("distanceMeters", "1.234")),
                inv("위도 91", p -> p.put("trackPoints", List.of(
                        point(91.0, 126.9780, "2026-11-12T09:00:00+09:00"),
                        point(37.5670, 126.9790, "2026-11-12T09:30:00+09:00")))),
                inv("경도 181", p -> p.put("trackPoints", List.of(
                        point(37.5665, 181.0, "2026-11-12T09:00:00+09:00"),
                        point(37.5670, 126.9790, "2026-11-12T09:30:00+09:00")))),
                inv("trackPoint recordedAt 누락", p -> {
                    Map<String, Object> noTime = point(37.5665, 126.9780, "2026-11-12T09:00:00+09:00");
                    noTime.remove("recordedAt");
                    p.put("trackPoints", List.of(noTime,
                            point(37.5670, 126.9790, "2026-11-12T09:30:00+09:00")));
                }),
                inv("trackPoints recordedAt 역순", p -> p.put("trackPoints", List.of(
                        point(37.5665, 126.9780, "2026-11-12T09:10:00+09:00"),
                        point(37.5670, 126.9790, "2026-11-12T09:00:00+09:00")))),
                inv("simulated null", p -> p.put("simulated", null))
        );
    }

    private static Arguments inv(String name, Consumer<Map<String, Object>> mutator) {
        return Arguments.of(name, mutator);
    }

    // =====================================================================
    // ACT-02 GET /api/v1/activities
    // =====================================================================
    @Nested
    @DisplayName("ACT-02 활동 기록 목록 조회")
    class GetActivities {

        @Test
        @DisplayName("ACT-02-S01 본인 기록만 최신순으로 페이지 조회한다")
        void S01_myActivitiesDescWithPaging() throws Exception {
            saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 11, 20, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 11, 10, 9, 0), 600, "1000.00", "WALK", false);

            // 최신순
            mockMvc.perform(get(URL).param("month", "2026-11"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(3)))
                    .andExpect(jsonPath("$.data.content[0].startedAt", startsWith("2026-11-20")))
                    .andExpect(jsonPath("$.data.content[1].startedAt", startsWith("2026-11-10")))
                    .andExpect(jsonPath("$.data.content[2].startedAt", startsWith("2026-11-03")))
                    .andExpect(jsonPath("$.data.content[0].routeName").value("활동 테스트 코스 A"))
                    .andExpect(jsonPath("$.data.content[0].startedAt", containsString("+09:00")));

            // 페이징
            mockMvc.perform(get(URL).param("month", "2026-11").param("page", "0").param("size", "2"))
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(true));

            mockMvc.perform(get(URL).param("month", "2026-11").param("page", "1").param("size", "2"))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("ACT-02-S02 month 필터가 적용된다")
        void S02_monthFilter() throws Exception {
            saveActivity(kst(2026, 10, 15, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 11, 15, 9, 0), 600, "1000.00", "WALK", false);

            mockMvc.perform(get(URL).param("month", "2026-11"))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].startedAt", startsWith("2026-11-15")));
        }

        @Test
        @DisplayName("ACT-02-S02 activityType 필터가 적용된다")
        void S02_activityTypeFilter() throws Exception {
            saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 11, 4, 9, 0), 600, "1000.00", "RUN", false);
            saveActivity(kst(2026, 11, 5, 9, 0), 600, "1000.00", "BIKE", false);

            mockMvc.perform(get(URL).param("month", "2026-11").param("activityType", "RUN"))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].activityType").value("RUN"));
        }

        @Test
        @DisplayName("ACT-02-F01 인증 누락은 401이다")
        void F01_unauthorized() throws Exception {
            unauthenticated();
            mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        }

        @ParameterizedTest(name = "ACT-02-F02 잘못된 파라미터 {0}={1} -> 400")
        @MethodSource("com.iriro.server.activity.presentation.ActivityScenarioTest#invalidListParams")
        void F02_invalidParams(String name, String value) throws Exception {
            mockMvc.perform(get(URL).param(name, value))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ACT-02-E01 size=100 은 허용한다")
        void E01_size100() throws Exception {
            mockMvc.perform(get(URL).param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(100));
            mockMvc.perform(get(URL).param("size", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ACT-02-E02 기록이 없으면 content=[] 와 totalElements=0 이다")
        void E02_empty() throws Exception {
            mockMvc.perform(get(URL).param("month", "2020-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(0)))
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("ACT-02-E03 다른 회원 기록은 목록에 없다")
        void E03_otherMembersHidden() throws Exception {
            loginAs(otherMemberId);
            saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", false);

            loginAs(memberId);
            mockMvc.perform(get(URL).param("month", "2026-11"))
                    .andExpect(jsonPath("$.data.content", hasSize(0)));
        }

        // ---------- 추가 ----------

        @Test
        @DisplayName("ACT-02-X01 (추가) month 를 생략하면 이번 달(KST) 활동이 조회된다")
        void X01_defaultMonth() throws Exception {
            saveActivity(OffsetDateTime.now(KST).minusMinutes(10), 300, "500.00", "WALK", false);

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
        }

        @Test
        @DisplayName("ACT-02-X02 (추가) 시연(simulated) 기록도 목록에는 포함된다")
        void X02_includesSimulated() throws Exception {
            saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", true);

            mockMvc.perform(get(URL).param("month", "2026-11"))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].simulated").value(true));
        }

        @Test
        @DisplayName("ACT-02-X03 (추가) 빈 activityType 은 필터 없이 전체 조회된다")
        void X03_blankTypeMeansNoFilter() throws Exception {
            saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 11, 4, 9, 0), 600, "1000.00", "RUN", false);

            mockMvc.perform(get(URL).param("month", "2026-11").param("activityType", " "))
                    .andExpect(jsonPath("$.data.content", hasSize(2)));
        }

        @Test
        @DisplayName("ACT-02-X04 (추가) 월 경계는 KST 기준이다 (10/31 23:30 은 10월, 11/01 00:00 은 11월)")
        void X04_monthBoundaryKst() throws Exception {
            saveActivity(kst(2026, 10, 31, 23, 30), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 11, 1, 0, 0), 600, "1000.00", "WALK", false);

            mockMvc.perform(get(URL).param("month", "2026-10"))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].startedAt", startsWith("2026-10-31T23:30")));

            mockMvc.perform(get(URL).param("month", "2026-11"))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].startedAt", startsWith("2026-11-01T00:00")));
        }
    }

    static Stream<Arguments> invalidListParams() {
        return Stream.of(
                Arguments.of("page", "-1"),
                Arguments.of("size", "0"),
                Arguments.of("size", "101"),
                Arguments.of("activityType", "SWIM"),
                Arguments.of("month", "2026-13"),
                Arguments.of("month", "abc")
        );
    }

    // =====================================================================
    // ACT-03 GET /api/v1/activities/{activityId}
    // =====================================================================
    @Nested
    @DisplayName("ACT-03 활동 기록 상세 조회")
    class GetActivityDetail {

        @Test
        @DisplayName("ACT-03-S01 본인 활동 상세에 거리·시간·페이스·실제 GPS 경로가 반환된다")
        void S01_detail() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("elapsedSeconds", 1800);
            payload.put("distanceMeters", "2500.00");              // 1800s / 2.5km = 720 s/km
            long id = readActivityId(create(UUID.randomUUID().toString(), payload).andReturn());

            mockMvc.perform(get(URL + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activityId").value(id))
                    .andExpect(jsonPath("$.data.routeId").value(routeId))
                    .andExpect(jsonPath("$.data.routeName").value("활동 테스트 코스 A"))
                    .andExpect(jsonPath("$.data.activityType").value("WALK"))
                    .andExpect(jsonPath("$.data.elapsedSeconds").value(1800))
                    .andExpect(jsonPath("$.data.distanceMeters").value(2500.0))
                    .andExpect(jsonPath("$.data.simulated").value(false))
                    .andExpect(jsonPath("$.data.averagePaceSecondsPerKm").value(720))
                    .andExpect(jsonPath("$.data.geometry.type").value("LineString"))
                    .andExpect(jsonPath("$.data.geometry.coordinates", hasSize(2)));
        }

        @Test
        @DisplayName("ACT-03-F01 없는 활동은 404이다")
        void F01_notFound() throws Exception {
            mockMvc.perform(get(URL + "/{id}", 999_999_999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
        }

        @Test
        @DisplayName("ACT-03-F01 다른 회원 활동은 404이다")
        void F01_otherMembersActivity() throws Exception {
            loginAs(otherMemberId);
            long othersId = saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", false);

            loginAs(memberId);
            mockMvc.perform(get(URL + "/{id}", othersId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
        }

        @Test
        @DisplayName("ACT-03-F02 인증 누락은 401이다")
        void F02_unauthorized() throws Exception {
            long id = saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", false);

            unauthenticated();
            mockMvc.perform(get(URL + "/{id}", id))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ACT-03-E01 GeoJSON 좌표 순서는 [경도, 위도]다")
        void E01_coordinateOrder() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("trackPoints", List.of(
                    point(37.5665, 126.9780, "2026-11-12T09:00:00+09:00"),
                    point(37.5670, 126.9790, "2026-11-12T09:30:00+09:00")
            ));
            long id = readActivityId(create(UUID.randomUUID().toString(), payload).andReturn());

            mockMvc.perform(get(URL + "/{id}", id))
                    .andExpect(jsonPath("$.data.geometry.coordinates[0][0]").value(126.9780))   // longitude
                    .andExpect(jsonPath("$.data.geometry.coordinates[0][1]").value(37.5665))    // latitude
                    .andExpect(jsonPath("$.data.geometry.coordinates[1][0]").value(126.9790))
                    .andExpect(jsonPath("$.data.geometry.coordinates[1][1]").value(37.5670));
        }

        @Test
        @DisplayName("ACT-03-E02 저장된 시작·종료 시각과 경로가 그대로 재조회된다 (UTC 로 보내도 KST 로 같은 시각)")
        void E02_roundTrip() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("startedAt", "2026-11-12T00:00:00Z");      // = 09:00 KST
            payload.put("endedAt", "2026-11-12T00:30:00Z");        // = 09:30 KST
            payload.put("trackPoints", List.of(
                    point(37.5665, 126.9780, "2026-11-12T00:00:00Z"),
                    point(37.5668, 126.9785, "2026-11-12T00:15:00Z"),
                    point(37.5670, 126.9790, "2026-11-12T00:30:00Z")
            ));
            long id = readActivityId(create(UUID.randomUUID().toString(), payload).andReturn());

            mockMvc.perform(get(URL + "/{id}", id))
                    .andExpect(jsonPath("$.data.startedAt", startsWith("2026-11-12T09:00")))
                    .andExpect(jsonPath("$.data.startedAt", containsString("+09:00")))
                    .andExpect(jsonPath("$.data.endedAt", startsWith("2026-11-12T09:30")))
                    .andExpect(jsonPath("$.data.geometry.coordinates", hasSize(3)))
                    .andExpect(jsonPath("$.data.geometry.coordinates[1][0]").value(126.9785))
                    .andExpect(jsonPath("$.data.geometry.coordinates[1][1]").value(37.5668))
                    .andExpect(jsonPath("$.data.geometry.coordinates[2][0]").value(126.9790));
        }

        // ---------- 추가 ----------

        @Test
        @DisplayName("ACT-03-X01 (추가) 시연 기록도 simulated=true 로 상세 조회된다")
        void X01_simulatedDetail() throws Exception {
            long id = saveActivity(kst(2026, 11, 3, 9, 0), 600, "1000.00", "WALK", true);

            mockMvc.perform(get(URL + "/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.simulated").value(true));
        }

        @Test
        @DisplayName("ACT-03-X02 (추가) activityId 가 숫자가 아니면 400이다")
        void X02_invalidPathVariable() throws Exception {
            mockMvc.perform(get(URL + "/{id}", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // ACT-04 GET /api/v1/activities/stats/weekly
    // =====================================================================
    @Nested
    @DisplayName("ACT-04 주간 활동 통계 조회")
    class WeeklyStats {

        @Test
        @DisplayName("ACT-04-S01 선택 날짜가 속한 KST 월~일 7일 통계와 최근 3개 활동을 반환한다")
        void S01_weekly() throws Exception {
            // 기준일 2026-11-11(수) -> 주간 2026-11-09(월) ~ 2026-11-15(일)
            saveActivity(kst(2026, 11, 9, 10, 0), 600, "1000.00", "WALK", false);    // 월
            saveActivity(kst(2026, 11, 11, 8, 0), 1200, "2000.00", "RUN", false);    // 수
            saveActivity(kst(2026, 11, 11, 18, 0), 1800, "3000.00", "BIKE", false);  // 수
            saveActivity(kst(2026, 11, 15, 23, 50), 300, "500.00", "WALK", false);   // 일
            saveActivity(kst(2026, 11, 10, 10, 0), 300, "900.00", "WALK", true);     // 시연 -> 제외

            mockMvc.perform(get(URL + "/stats/weekly").param("date", "2026-11-11"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.weekStartDate").value("2026-11-09"))
                    .andExpect(jsonPath("$.data.weekEndDate").value("2026-11-15"))
                    .andExpect(jsonPath("$.data.activityCount").value(4))
                    .andExpect(jsonPath("$.data.distanceMeters").value(6500.0))
                    .andExpect(jsonPath("$.data.elapsedSeconds").value(3900))
                    // 일별 7일
                    .andExpect(jsonPath("$.data.daily", hasSize(7)))
                    .andExpect(jsonPath("$.data.daily[0].date").value("2026-11-09"))
                    .andExpect(jsonPath("$.data.daily[0].activityCount").value(1))
                    .andExpect(jsonPath("$.data.daily[1].activityCount").value(0))   // 시연 기록 제외
                    .andExpect(jsonPath("$.data.daily[2].date").value("2026-11-11"))
                    .andExpect(jsonPath("$.data.daily[2].activityCount").value(2))
                    .andExpect(jsonPath("$.data.daily[2].distanceMeters").value(5000.0))
                    .andExpect(jsonPath("$.data.daily[2].elapsedSeconds").value(3000))
                    .andExpect(jsonPath("$.data.daily[6].date").value("2026-11-15"))
                    .andExpect(jsonPath("$.data.daily[6].activityCount").value(1))
                    // 최근 활동 3건, 최신순
                    .andExpect(jsonPath("$.data.recentActivities", hasSize(3)))
                    .andExpect(jsonPath("$.data.recentActivities[0].startedAt", startsWith("2026-11-15T23:50")))
                    .andExpect(jsonPath("$.data.recentActivities[1].startedAt", startsWith("2026-11-11T18:00")))
                    .andExpect(jsonPath("$.data.recentActivities[2].startedAt", startsWith("2026-11-11T08:00")))
                    .andExpect(jsonPath("$.data.recentActivities[0].routeName").value("활동 테스트 코스 A"));
        }

        @Test
        @DisplayName("ACT-04-F01 인증 누락은 401이다")
        void F01_unauthorized() throws Exception {
            unauthenticated();
            mockMvc.perform(get(URL + "/stats/weekly")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ACT-04-F01 date 형식 오류는 400이다")
        void F01_invalidDate() throws Exception {
            mockMvc.perform(get(URL + "/stats/weekly").param("date", "2026/11/11"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(URL + "/stats/weekly").param("date", "2026-02-30"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ACT-04-E01 일요일 23:59 까지는 이번 주, 월요일 00:00 부터는 다음 주다")
        void E01_sundayMondayBoundary() throws Exception {
            saveActivity(kst(2026, 11, 8, 23, 59), 60, "100.00", "WALK", false);    // 지난주 일요일 -> 제외
            saveActivity(kst(2026, 11, 9, 0, 0), 60, "200.00", "WALK", false);      // 월요일 00:00 -> 포함
            saveActivity(kst(2026, 11, 15, 23, 59), 60, "300.00", "WALK", false);   // 일요일 23:59 -> 포함
            saveActivity(kst(2026, 11, 16, 0, 0), 60, "400.00", "WALK", false);     // 다음주 월요일 -> 제외

            // 기준일이 월요일이든 일요일이든 같은 주
            for (String date : List.of("2026-11-09", "2026-11-15")) {
                mockMvc.perform(get(URL + "/stats/weekly").param("date", date))
                        .andExpect(jsonPath("$.data.weekStartDate").value("2026-11-09"))
                        .andExpect(jsonPath("$.data.weekEndDate").value("2026-11-15"))
                        .andExpect(jsonPath("$.data.activityCount").value(2))
                        .andExpect(jsonPath("$.data.distanceMeters").value(500.0))
                        .andExpect(jsonPath("$.data.daily[0].activityCount").value(1))
                        .andExpect(jsonPath("$.data.daily[6].activityCount").value(1));
            }
        }

        @Test
        @DisplayName("ACT-04-E02 활동 없는 요일은 0이고 다른 회원 기록은 합산되지 않는다")
        void E02_emptyDaysAndOtherMembers() throws Exception {
            loginAs(otherMemberId);
            saveActivity(kst(2026, 11, 11, 8, 0), 600, "1000.00", "WALK", false);

            loginAs(memberId);
            saveActivity(kst(2026, 11, 12, 8, 0), 600, "2000.00", "WALK", false);

            mockMvc.perform(get(URL + "/stats/weekly").param("date", "2026-11-11"))
                    .andExpect(jsonPath("$.data.activityCount").value(1))
                    .andExpect(jsonPath("$.data.distanceMeters").value(2000.0))
                    .andExpect(jsonPath("$.data.daily[2].activityCount").value(0))   // 수요일(타 회원 기록만 있음)
                    .andExpect(jsonPath("$.data.daily[2].distanceMeters").value(0))
                    .andExpect(jsonPath("$.data.daily[3].activityCount").value(1));  // 목요일

            // 활동이 전혀 없는 주
            mockMvc.perform(get(URL + "/stats/weekly").param("date", "2020-01-15"))
                    .andExpect(jsonPath("$.data.activityCount").value(0))
                    .andExpect(jsonPath("$.data.daily", hasSize(7)))
                    .andExpect(jsonPath("$.data.recentActivities", hasSize(0)));
        }

        @Test
        @DisplayName("ACT-04-X01 (추가) date 를 생략해도 200이다")
        void X01_defaultDate() throws Exception {
            mockMvc.perform(get(URL + "/stats/weekly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.daily", hasSize(7)));
        }
    }

    // =====================================================================
    // ACT-05 GET /api/v1/activities/stats/monthly
    // =====================================================================
    @Nested
    @DisplayName("ACT-05 월간 활동 통계 조회")
    class MonthlyStats {

        @Test
        @DisplayName("ACT-05-S01 선택 월의 요약·활동 날짜·현재/최장 연속 활동 일수를 반환한다")
        void S01_monthly() throws Exception {
            // 2026-10 (과거 월 -> "오늘"에 의존하지 않음)
            // 포함: 10/01, 10/02, 10/03, 10/10(2건), 10/30, 10/31
            saveActivity(kst(2026, 10, 1, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 10, 2, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 10, 3, 9, 0), 600, "1000.00", "RUN", false);
            saveActivity(kst(2026, 10, 10, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 10, 10, 20, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 10, 30, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 10, 31, 9, 0), 600, "1000.00", "BIKE", false);
            // 제외: 시연 기록
            saveActivity(kst(2026, 10, 15, 9, 0), 300, "900.00", "WALK", true);

            mockMvc.perform(get(URL + "/stats/monthly").param("month", "2026-10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.month").value("2026-10"))
                    .andExpect(jsonPath("$.data.activityCount").value(7))
                    .andExpect(jsonPath("$.data.distanceMeters").value(7000.0))
                    .andExpect(jsonPath("$.data.elapsedSeconds").value(4200))
                    // 같은 날 2건은 날짜 1개, 오름차순
                    .andExpect(jsonPath("$.data.activityDates", hasSize(6)))
                    .andExpect(jsonPath("$.data.activityDates[0]").value("2026-10-01"))
                    .andExpect(jsonPath("$.data.activityDates[3]").value("2026-10-10"))
                    .andExpect(jsonPath("$.data.activityDates[5]").value("2026-10-31"))
                    .andExpect(jsonPath("$.data.longestStreakDays").value(3))   // 10/01~10/03
                    .andExpect(jsonPath("$.data.currentStreakDays").value(2));  // 10/30~10/31 (월 말일 기준)
        }

        @Test
        @DisplayName("ACT-05-F01 인증 누락은 401이다")
        void F01_unauthorized() throws Exception {
            unauthenticated();
            mockMvc.perform(get(URL + "/stats/monthly")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ACT-05-F01 month 형식 오류는 400이다")
        void F01_invalidMonth() throws Exception {
            mockMvc.perform(get(URL + "/stats/monthly").param("month", "2026-13"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(URL + "/stats/monthly").param("month", "202610"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ACT-05-E01 월말·월초 연속 활동과 KST 자정 경계를 검증한다")
        void E01_monthEndStartAndKstMidnight() throws Exception {
            saveActivity(kst(2026, 10, 30, 9, 0), 60, "100.00", "WALK", false);
            // 2026-10-31T14:59Z = 10/31 23:59 KST -> 10월
            saveActivity(utc(2026, 10, 31, 14, 59), 60, "100.00", "WALK", false);
            // 2026-10-31T15:00Z = 11/01 00:00 KST -> 11월
            saveActivity(utc(2026, 10, 31, 15, 0), 60, "100.00", "WALK", false);
            saveActivity(kst(2026, 11, 2, 9, 0), 60, "100.00", "WALK", false);

            // 10월: 10/30, 10/31 (월 경계를 넘는 연속은 월 단위로 끊어서 계산)
            mockMvc.perform(get(URL + "/stats/monthly").param("month", "2026-10"))
                    .andExpect(jsonPath("$.data.activityCount").value(2))
                    .andExpect(jsonPath("$.data.activityDates", hasSize(2)))
                    .andExpect(jsonPath("$.data.activityDates[1]").value("2026-10-31"))
                    .andExpect(jsonPath("$.data.longestStreakDays").value(2))
                    .andExpect(jsonPath("$.data.currentStreakDays").value(2));

            // 11월: 11/01, 11/02 (현재 연속 일수는 "오늘"에 의존하므로 검증하지 않음)
            mockMvc.perform(get(URL + "/stats/monthly").param("month", "2026-11"))
                    .andExpect(jsonPath("$.data.activityCount").value(2))
                    .andExpect(jsonPath("$.data.activityDates[0]").value("2026-11-01"))
                    .andExpect(jsonPath("$.data.activityDates[1]").value("2026-11-02"))
                    .andExpect(jsonPath("$.data.longestStreakDays").value(2));
        }

        /**
         * 이 테스트는 현재 ActivityService 구현에서 실패한다.
         * 구현은 "오늘"부터 거꾸로 세기 때문에 오늘 기록이 없으면 어제 기록이 있어도 0이 된다.
         */
        @Test
        @DisplayName("ACT-05-E02 오늘 기록이 없고 어제 기록이 있으면 현재 연속 일수에 어제를 포함한다")
        void E02_yesterdayCountsWhenNoActivityToday() throws Exception {
            LocalDate today = LocalDate.now(SEOUL);
            // 어제·그저께가 이번 달 안에 있어야 한다 (매월 1·2일에는 건너뜀)
            Assumptions.assumeTrue(today.getDayOfMonth() >= 3);

            saveActivity(today.minusDays(1).atTime(12, 0).atOffset(KST), 600, "1000.00", "WALK", false);
            saveActivity(today.minusDays(2).atTime(12, 0).atOffset(KST), 600, "1000.00", "WALK", false);

            mockMvc.perform(get(URL + "/stats/monthly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.currentStreakDays").value(2));
        }

        @Test
        @DisplayName("ACT-05-E03 기록이 없는 월은 0과 빈 활동 날짜다")
        void E03_emptyMonth() throws Exception {
            mockMvc.perform(get(URL + "/stats/monthly").param("month", "2020-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activityCount").value(0))
                    .andExpect(jsonPath("$.data.distanceMeters").value(0))
                    .andExpect(jsonPath("$.data.elapsedSeconds").value(0))
                    .andExpect(jsonPath("$.data.activityDates", hasSize(0)))
                    .andExpect(jsonPath("$.data.currentStreakDays").value(0))
                    .andExpect(jsonPath("$.data.longestStreakDays").value(0));
        }

        // ---------- 추가 ----------

        @Test
        @DisplayName("ACT-05-X01 (추가) 다른 회원 활동은 합산되지 않는다")
        void X01_otherMembersExcluded() throws Exception {
            loginAs(otherMemberId);
            saveActivity(kst(2026, 10, 5, 9, 0), 600, "1000.00", "WALK", false);

            loginAs(memberId);
            mockMvc.perform(get(URL + "/stats/monthly").param("month", "2026-10"))
                    .andExpect(jsonPath("$.data.activityCount").value(0));
        }

        @Test
        @DisplayName("ACT-05-X02 (추가) 과거 월의 말일에 활동이 없으면 현재 연속 일수는 0이다")
        void X02_pastMonthNoActivityOnLastDay() throws Exception {
            saveActivity(kst(2026, 10, 5, 9, 0), 600, "1000.00", "WALK", false);
            saveActivity(kst(2026, 10, 6, 9, 0), 600, "1000.00", "WALK", false);

            mockMvc.perform(get(URL + "/stats/monthly").param("month", "2026-10"))
                    .andExpect(jsonPath("$.data.longestStreakDays").value(2))
                    .andExpect(jsonPath("$.data.currentStreakDays").value(0));
        }

        @Test
        @DisplayName("ACT-05-X03 (추가) month 를 생략해도 200이다")
        void X03_defaultMonth() throws Exception {
            mockMvc.perform(get(URL + "/stats/monthly"))
                    .andExpect(status().isOk());
        }
    }

    // =====================================================================
    // 헬퍼
    // =====================================================================

    private void loginAs(Long id) {
        when(currentMemberProvider.requireMemberId()).thenReturn(id);
    }

    private void unauthenticated() {
        when(currentMemberProvider.requireMemberId())
                .thenThrow(new CustomException(ErrorCode.UNAUTHORIZED_USER));
    }

    private ResultActions create(String idempotencyKey, Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post(URL)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private long readActivityId(MvcResult result) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("activityId")
                .asLong();
    }

    /** 현재 로그인 회원으로 활동을 저장하고 activityId 반환 (routeId / startPointId 는 기본 코스 A) */
    private long saveActivity(
            OffsetDateTime startedAt,
            int elapsedSeconds,
            String distanceMeters,
            String activityType,
            boolean simulated
    ) throws Exception {
        OffsetDateTime endedAt = startedAt.plusSeconds(elapsedSeconds);

        Map<String, Object> payload = validPayload();
        payload.put("activityType", activityType);
        payload.put("startedAt", ISO.format(startedAt));
        payload.put("endedAt", ISO.format(endedAt));
        payload.put("elapsedSeconds", elapsedSeconds);
        payload.put("distanceMeters", distanceMeters);
        payload.put("simulated", simulated);
        payload.put("trackPoints", List.of(
                point(37.5665, 126.9780, ISO.format(startedAt)),
                point(37.5670, 126.9790, ISO.format(endedAt))
        ));

        return readActivityId(
                create(UUID.randomUUID().toString(), payload)
                        .andExpect(status().isCreated())
                        .andReturn()
        );
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("routeId", routeId);
        payload.put("startPointId", startPointId);
        payload.put("activityType", "WALK");
        payload.put("startedAt", "2026-11-12T09:00:00+09:00");
        payload.put("endedAt", "2026-11-12T09:30:00+09:00");
        payload.put("elapsedSeconds", 1800);
        payload.put("distanceMeters", "2500.00");
        payload.put("trackPoints", new ArrayList<>(List.of(
                point(37.5665, 126.9780, "2026-11-12T09:00:00+09:00"),
                point(37.5670, 126.9790, "2026-11-12T09:30:00+09:00")
        )));
        payload.put("saveAsFavorite", false);
        payload.put("simulated", false);
        return payload;
    }

    private static Map<String, Object> point(double latitude, double longitude, String recordedAt) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("latitude", latitude);
        point.put("longitude", longitude);
        point.put("recordedAt", recordedAt);
        return point;
    }

    private static OffsetDateTime kst(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, KST);
    }

    private static OffsetDateTime utc(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC);
    }

    // =====================================================================
    // 테스트 데이터 준비
    //  - Member 는 public 생성자가 있어 Repository 로 저장
    //  - Trail / Route / RouteStartPoint 는 생성자·setter 가 없어 JdbcTemplate 으로 직접 insert
    //  - JdbcTemplate 도 JPA 와 같은 트랜잭션(같은 DataSource 커넥션)을 공유하므로
    //    클래스의 @Transactional 롤백으로 함께 정리된다.
    // =====================================================================

    private Long createMember(String name) {
        return memberRepository.saveAndFlush(new Member(name, name.toLowerCase())).getId();
    }

    private Long createTrail(String name) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return insert("""
                        INSERT INTO trails
                            (name, center_latitude, center_longitude, length_meters,
                             geometry_geojson, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                name, 37.5665, 126.9780, 5000.00,
                "{\"type\":\"LineString\",\"coordinates\":[[126.9780,37.5665],[126.9790,37.5670]]}",
                now, now);
    }

    private Long createRoute(Long trailId, String name) {
        return createRoute(trailId, name, List.of("WALK", "RUN", "BIKE"));
    }

    private Long createRoute(Long trailId, String name, List<String> activityTypes) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Long id = insert("""
                        INSERT INTO routes
                            (trail_id, name, distance_meters, estimated_duration_seconds,
                             geometry_geojson, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                trailId, name, 2500.00, 1800,
                "{\"type\":\"LineString\",\"coordinates\":[[126.9780,37.5665],[126.9790,37.5670]]}",
                now, now);

        // activities 의 FK (route_id, activity_type) -> route_activity_types 때문에
        // 코스가 지원하는 활동 유형을 함께 등록해야 활동을 저장할 수 있다.
        for (String type : activityTypes) {
            jdbcTemplate.update(
                    "INSERT INTO route_activity_types (route_id, activity_type) VALUES (?, ?)",
                    id, type);
        }
        return id;
    }

    private Long createStartPoint(Long routeId) {
        return insert("""
                        INSERT INTO route_start_points (route_id, name, latitude, longitude)
                        VALUES (?, ?, ?, ?)
                        """,
                routeId, "테스트 출발점", 37.5665, 126.9780);
    }

    /** INSERT 후 생성된 id 반환 */
    private Long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}