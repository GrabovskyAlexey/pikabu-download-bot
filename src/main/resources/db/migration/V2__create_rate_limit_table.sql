CREATE TABLE rate_limits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP
);

CREATE INDEX idx_user_id_rate ON rate_limits(user_id);
