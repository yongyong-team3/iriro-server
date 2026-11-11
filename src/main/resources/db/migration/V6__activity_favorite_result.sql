-- 동일 Idempotency-Key 재시도 시 첫 응답의 favoriteAdded를 재현한다.
ALTER TABLE activities ADD COLUMN favorite_added BOOLEAN NOT NULL DEFAULT FALSE;
