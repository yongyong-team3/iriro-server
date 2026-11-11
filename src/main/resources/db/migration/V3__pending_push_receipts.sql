CREATE TABLE pending_push_receipts (
    ticket_id VARCHAR(128) NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    push_token VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id),
    KEY ix_pending_push_receipts_created (created_at),
    CONSTRAINT fk_pending_push_receipts_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
