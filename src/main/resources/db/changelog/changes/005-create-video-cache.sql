--liquibase formatted sql

--changeset pikabu-bot:5
--comment: Create video_cache table for Telegram file_id caching

CREATE TABLE pikabu_bot.video_cache (
    video_url VARCHAR(2048) PRIMARY KEY,
    file_id VARCHAR(256) NOT NULL,
    file_size BIGINT,
    cached_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_last_used_at ON pikabu_bot.video_cache(last_used_at);
