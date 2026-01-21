-- liquibase formatted sql

-- changeset system:006-create-auth-config
CREATE TABLE IF NOT EXISTS pikabu_bot.auth_config
(
    id            BIGSERIAL PRIMARY KEY,
    config_key    VARCHAR(50)   NOT NULL UNIQUE,
    config_value  TEXT,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(100)
);

COMMENT ON TABLE pikabu_bot.auth_config IS 'Конфигурация авторизации (cookies и др.)';
COMMENT ON COLUMN pikabu_bot.auth_config.config_key IS 'Ключ конфигурации (например, pikabu_cookies)';
COMMENT ON COLUMN pikabu_bot.auth_config.config_value IS 'Значение конфигурации';
COMMENT ON COLUMN pikabu_bot.auth_config.updated_at IS 'Время последнего обновления';
COMMENT ON COLUMN pikabu_bot.auth_config.updated_by IS 'Кто обновил (admin_id)';
