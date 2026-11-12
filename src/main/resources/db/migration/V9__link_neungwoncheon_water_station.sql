UPDATE trails
SET water_station_code = '1016607',
    updated_at = UTC_TIMESTAMP(6)
WHERE name = '능원천 하천길'
  AND section_name = '월촌교~매산교 구간'
  AND water_station_code IS NULL;
