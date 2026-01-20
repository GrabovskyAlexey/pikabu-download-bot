--liquibase formatted sql

--changeset pikabu-bot:2
--comment: Create rate_limits table

CREATE TABLE pikabu_bot.rate_limits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP
);

CREATE INDEX idx_user_id_rate ON pikabu_bot.rate_limits(user_id);
