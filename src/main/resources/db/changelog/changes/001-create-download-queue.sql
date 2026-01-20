--liquibase formatted sql

--changeset pikabu-bot:1
--comment: Create download_queue table

CREATE TABLE pikabu_bot.download_queue (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message_id INTEGER NOT NULL,
    video_url VARCHAR(2048) NOT NULL,
    video_title VARCHAR(512),
    status VARCHAR(50) NOT NULL,
    position INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_status_created ON pikabu_bot.download_queue(status, created_at);
CREATE INDEX idx_user_id ON pikabu_bot.download_queue(user_id);
