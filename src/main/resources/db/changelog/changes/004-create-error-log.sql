--liquibase formatted sql

--changeset pikabu-bot:4
--comment: Create error_log table

CREATE TABLE pikabu_bot.error_log (
    id BIGSERIAL PRIMARY KEY,
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT NOT NULL,
    page_url VARCHAR(2048),
    stack_trace TEXT,
    notified_admin BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_error_type_occurred ON pikabu_bot.error_log(error_type, occurred_at);
CREATE INDEX idx_notified_admin ON pikabu_bot.error_log(notified_admin);
