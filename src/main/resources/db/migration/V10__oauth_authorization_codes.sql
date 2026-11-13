CREATE TABLE oauth_states (
    state_hash CHAR(64) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    redirect_uri VARCHAR(2048) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (state_hash),
    KEY ix_oauth_states_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oauth_login_codes (
    code_hash CHAR(64) NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    is_new_member BOOLEAN NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (code_hash),
    KEY ix_oauth_login_codes_expiry (expires_at),
    CONSTRAINT fk_oauth_login_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
