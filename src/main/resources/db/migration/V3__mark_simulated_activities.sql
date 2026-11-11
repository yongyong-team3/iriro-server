-- Existing rows retain the prior behavior until their origin can be audited.
-- Do not infer which historical records came from the 20x demonstration.
ALTER TABLE activities
    ADD COLUMN simulated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_activity_member_simulated_started
    ON activities (member_id, simulated, started_at);
