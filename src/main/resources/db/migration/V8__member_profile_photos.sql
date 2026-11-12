CREATE TABLE member_profile_photos (
    member_id BIGINT UNSIGNED NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    content LONGBLOB NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT fk_profile_photo_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
