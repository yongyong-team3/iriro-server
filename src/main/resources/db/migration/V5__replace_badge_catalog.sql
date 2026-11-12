-- 기획에서 기존 3종 배지를 8종으로 대체했다. 이전 기준의 획득 기록은 새 배지로 승계하지 않는다.
DELETE FROM badge_awards WHERE badge_code IN ('FIRST_ACTIVITY', 'FIVE_ACTIVITIES', 'TEN_KM');
