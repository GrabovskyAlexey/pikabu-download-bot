--liquibase formatted sql

--changeset pikabu-bot:3
--comment: Create download_history table

CREATE TABLE pikabu_bot.download_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_url VARCHAR(2048) NOT NULL,
    video_title VARCHAR(512),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_user_id_completed ON pikabu_bot.download_history(user_id, completed_at);
