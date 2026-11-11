package com.iriro.server.river.application;

import com.iriro.server.common.application.ApiException;
import com.iriro.server.river.domain.HistoricalStatusSnapshot;
import com.iriro.server.river.infrastructure.HistoricalStatusSnapshotRepository;
import com.iriro.server.river.presentation.HistoricalWaterStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
public class HistoricalWaterStatusService {
    private static final String WOLCHON_STATION_CODE = "1016607";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final HistoricalStatusSnapshotRepository repository;

    public HistoricalWaterStatusService(HistoricalStatusSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public HistoricalWaterStatusResponse get(String at, String stationCode) {
        if (at == null || at.isBlank()) throw invalid();
        OffsetDateTime requested;
        try {
            requested = OffsetDateTime.parse(at);
        } catch (DateTimeException exception) {
            throw invalid();
        }
        if (!WOLCHON_STATION_CODE.equals(stationCode) || requested.toInstant().isAfter(Instant.now())) {
            throw invalid();
        }
        LocalDateTime requestedUtc = LocalDateTime.ofInstant(requested.toInstant(), ZoneOffset.UTC);
        HistoricalStatusSnapshot snapshot = repository.findLatestAvailable(
                stationCode, requestedUtc.minusMinutes(10), requestedUtc)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "HISTORICAL_SNAPSHOT_NOT_FOUND", "해당 시각의 저장된 수위·AI 판정이 없습니다."));
        return new HistoricalWaterStatusResponse(
                snapshot.getStationCode(), korea(requestedUtc), korea(snapshot.getObservedAt()),
                snapshot.getWaterLevelMeters(), snapshot.getWaterStatus(),
                snapshot.getPredictedWaterLevelMeters(), korea(snapshot.getForecastTargetAt()), snapshot.getTrend(),
                snapshot.getRecoveryStatus(), snapshot.getRecoveryEtaAtLeastMinutes(),
                korea(snapshot.getAiEvaluatedAt()), korea(snapshot.getSnapshotRecordedAt()),
                snapshot.getForecastModelVersion(), snapshot.getRecoveryModelVersion(),
                snapshot.getDataSource(), snapshot.getDataStatus(), snapshot.getOfficialControlStatus());
    }

    private OffsetDateTime korea(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toOffsetDateTime();
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
    }
}
