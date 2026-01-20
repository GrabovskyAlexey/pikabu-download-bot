#!/bin/bash

# Setup script для production сервера
# Запустите этот скрипт ОДИН РАЗ для подготовки сервера к deployment

set -e  # Прерываем выполнение при ошибке

echo "================================================"
echo "  Pikabu Bot - Production Server Setup"
echo "================================================"
echo ""

# Проверка, что скрипт запущен не от root
if [ "$EUID" -eq 0 ]; then
    echo "❌ Не запускайте этот скрипт от root!"
    echo "   Запустите от обычного пользователя с sudo правами"
    exit 1
fi

# 1. Установка Docker (если не установлен)
echo "📦 Шаг 1: Проверка Docker..."
if ! command -v docker &> /dev/null; then
    echo "Docker не найден. Устанавливаем..."
    curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
    sudo sh /tmp/get-docker.sh
    sudo usermod -aG docker $USER
    echo "✅ Docker установлен"
    echo "⚠️  Нужно перелогиниться для применения прав docker группы!"
    echo "   Запустите: newgrp docker"
else
    echo "✅ Docker уже установлен"
fi

# Проверка Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose не найден!"
    echo "   Установите Docker Compose или используйте docker compose (встроенный)"
    exit 1
fi
echo "✅ Docker Compose доступен"

# 2. Создание директории приложения
echo ""
echo "📁 Шаг 2: Создание директорий..."
sudo mkdir -p /opt/pikabu-bot
sudo chown $USER:$USER /opt/pikabu-bot
cd /opt/pikabu-bot

# Создаем поддиректории
mkdir -p logs data

echo "✅ Директории созданы: /opt/pikabu-bot"

# 3. Создание .env файла
echo ""
echo "⚙️  Шаг 3: Создание .env файла..."

if [ -f .env ]; then
    echo "⚠️  Файл .env уже существует. Создаем backup..."
    cp .env .env.backup.$(date +%Y%m%d_%H%M%S)
fi

cat > .env << 'EOF'
# =================================
# Pikabu Download Bot Configuration
# =================================

# Telegram Bot (ОБЯЗАТЕЛЬНО!)
BOT_TOKEN=your_bot_token_here
BOT_USERNAME=your_bot_username

# Admin (ОБЯЗАТЕЛЬНО!)
ADMIN_USER_ID=0

# Admin Notifications
ADMIN_ENABLE_NOTIFICATIONS=true
ADMIN_ENABLE_DAILY_DIGEST=false

# Database
DB_NAME=pikabu_bot
DB_USER=pikabu_user
DB_PASSWORD=change_this_password

# Application
SERVER_PORT=8080
APP_MAX_CONCURRENT_DOWNLOADS=5
APP_RATE_LIMIT_MAX_REQUESTS=1000
APP_RATE_LIMIT_WINDOW_HOURS=1

# Spring Datasource (используется внутри контейнера)
# currentSchema=pikabu_bot гарантирует использование правильной схемы
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/pikabu_bot?currentSchema=pikabu_bot
SPRING_DATASOURCE_USERNAME=pikabu_user
SPRING_DATASOURCE_PASSWORD=change_this_password
EOF

chmod 600 .env
echo "✅ Файл .env создан"

# 4. Создание docker-compose.yml
echo ""
echo "🐳 Шаг 4: Создание docker-compose.yml..."

cat > docker-compose.yml << 'EOF'
version: '3.9'

services:
  bot:
    image: ghcr.io/yourusername/pikabu-download-bot:latest
    container_name: pikabu-bot
    restart: unless-stopped
    ports:
      - "${SERVER_PORT:-8080}:${SERVER_PORT:-8080}"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=${SERVER_PORT:-8080}
      - BOT_TOKEN=${BOT_TOKEN}
      - BOT_USERNAME=${BOT_USERNAME}
      - ADMIN_USER_ID=${ADMIN_USER_ID}
      - ADMIN_ENABLE_NOTIFICATIONS=${ADMIN_ENABLE_NOTIFICATIONS:-true}
      - ADMIN_ENABLE_DAILY_DIGEST=${ADMIN_ENABLE_DAILY_DIGEST:-false}
      - SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}
      - SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD}
      - APP_MAX_CONCURRENT_DOWNLOADS=${APP_MAX_CONCURRENT_DOWNLOADS:-5}
      - APP_RATE_LIMIT_MAX_REQUESTS=${APP_RATE_LIMIT_MAX_REQUESTS:-1000}
      - APP_RATE_LIMIT_WINDOW_HOURS=${APP_RATE_LIMIT_WINDOW_HOURS:-1}
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - pikabu-network
    volumes:
      - ./logs:/app/logs
      - /tmp/pikabu-bot:/tmp
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  postgres:
    image: postgres:16-alpine
    container_name: pikabu-postgres
    restart: unless-stopped
    environment:
      - POSTGRES_DB=${DB_NAME:-pikabu_bot}
      - POSTGRES_USER=${DB_USER:-pikabu_user}
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - pikabu-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-pikabu_user}"]
      interval: 10s
      timeout: 5s
      retries: 5
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

networks:
  pikabu-network:
    driver: bridge

volumes:
  postgres-data:
EOF

echo "✅ Файл docker-compose.yml создан"

# 5. Настройка firewall (опционально)
echo ""
echo "🔒 Шаг 5: Настройка firewall (опционально)..."
read -p "Хотите настроить UFW firewall? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    if command -v ufw &> /dev/null; then
        sudo ufw allow ssh
        sudo ufw allow 8080/tcp
        sudo ufw --force enable
        echo "✅ Firewall настроен (открыты порты: SSH, 8080)"
    else
        echo "⚠️  UFW не установлен. Установите: sudo apt install ufw"
    fi
else
    echo "⏭️  Пропускаем настройку firewall"
fi

# 6. Создание systemd service (опционально)
echo ""
echo "🔄 Шаг 6: Автозапуск при перезагрузке..."
read -p "Настроить автозапуск через systemd? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    sudo tee /etc/systemd/system/pikabu-bot.service > /dev/null << EOF
[Unit]
Description=Pikabu Download Bot
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/pikabu-bot
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=0
User=$USER

[Install]
WantedBy=multi-user.target
EOF

    sudo systemctl daemon-reload
    sudo systemctl enable pikabu-bot.service
    echo "✅ Systemd service создан и включен"
else
    echo "⏭️  Пропускаем настройку systemd"
fi

# Финальные инструкции
echo ""
echo "================================================"
echo "  ✅ Setup завершен!"
echo "================================================"
echo ""
echo "📝 ВАЖНО: Отредактируйте файл .env!"
echo ""
echo "   cd /opt/pikabu-bot"
echo "   nano .env"
echo ""
echo "   Обязательно укажите:"
echo "   - BOT_TOKEN (получите у @BotFather)"
echo "   - BOT_USERNAME (ваш бот username без @)"
echo "   - ADMIN_USER_ID (ваш Telegram ID)"
echo "   - DB_PASSWORD (смените на безопасный пароль)"
echo ""
echo "📋 Следующие шаги:"
echo ""
echo "   1. Отредактируйте .env файл"
echo "   2. В docker-compose.yml замените 'yourusername' на ваш GitHub username"
echo "   3. Добавьте SSH публичный ключ для GitHub Actions:"
echo "      cat ~/.ssh/authorized_keys"
echo ""
echo "   4. Для первого запуска выполните:"
echo "      docker compose pull"
echo "      docker compose up -d"
echo ""
echo "   5. Проверьте логи:"
echo "      docker compose logs -f bot"
echo ""
echo "🚀 После этого GitHub Actions сможет автоматически деплоить!"
echo ""
