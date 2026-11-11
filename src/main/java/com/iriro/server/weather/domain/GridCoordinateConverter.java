
package com.iriro.server.weather.domain;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GridCoordinateConverter {

    // 지구 반경 (km)
    private static final double RE = 6371.00877;

    // 격자 간격 (km)
    private static final double GRID = 5.0;

    // 표준 위도
    private static final double SLAT1 = 30.0;
    private static final double SLAT2 = 60.0;

    // 기준점
    private static final double OLON = 126.0;
    private static final double OLAT = 38.0;

    // 기준 격자 좌표
    private static final double XO = 43.0;
    private static final double YO = 136.0;

    private static final double DEGRAD = Math.PI / 180.0;

    @Getter
    public static class GridXY {

        private final int x;
        private final int y;

        public GridXY(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * 위도/경도(WGS84)를 기상청 격자 좌표로 변환
     *
     * @param lat 위도
     * @param lon 경도
     * @return 기상청 격자 좌표
     */
    public GridXY convertToGrid(double lat, double lon) {

        // 1. 입력 좌표 검증
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            throw new IllegalArgumentException(
                    "위도와 경도는 유효한 숫자여야 합니다."
            );
        }

        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException(
                    "잘못된 위도입니다. lat=" + lat
            );
        }

        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException(
                    "잘못된 경도입니다. lon=" + lon
            );
        }

        log.info(
                "기상청 격자 변환 시작 - latitude: {}, longitude: {}",
                lat,
                lon
        );

        // 2. 좌표 변환 기본값 계산
        double re = RE / GRID;

        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;

        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        // 3. LCC 투영 상수 계산
        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5)
                / Math.tan(Math.PI * 0.25 + slat1 * 0.5);

        sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
                / Math.log(sn);

        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);

        sf = Math.pow(sf, sn)
                * Math.cos(slat1) / sn;

        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);

        ro = re * sf / Math.pow(ro, sn);

        // 4. 입력 위도에 대한 투영 좌표 계산
        double ra = Math.tan(
                Math.PI * 0.25 + lat * DEGRAD * 0.5
        );

        ra = re * sf / Math.pow(ra, sn);

        // 5. 경도 차이 계산
        double theta = lon * DEGRAD - olon;

        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }

        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }

        theta *= sn;

        // 6. 기상청 격자 좌표 계산
        int x = (int) Math.floor(
                ra * Math.sin(theta) + XO + 0.5
        );

        int y = (int) Math.floor(
                ro - ra * Math.cos(theta) + YO + 0.5
        );

        // 7. 변환 결과 확인
        log.info(
                "기상청 격자 변환 완료 - nx: {}, ny: {}",
                x,
                y
        );

        return new GridXY(x, y);
    }
}
