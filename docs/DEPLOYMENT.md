# 🚀 Deployment Guide

Полное руководство по deployment Pikabu Download Bot на production сервер.

## Оглавление

- [Подготовка сервера](#подготовка-сервера)
- [Настройка GitHub Secrets](#настройка-github-secrets)
- [CI/CD Pipeline](#cicd-pipeline)
- [Ручной deployment](#ручной-deployment)
- [Troubleshooting](#troubleshooting)

## Подготовка сервера

### Требования

- **OS:** Ubuntu 20.04+ / Debian 11+ (или любой Linux с Docker)
- **RAM:** Минимум 2GB (рекомендуется 4GB)
- **Disk:** Минимум 10GB свободного места
- **Network:** Открытый порт для PostgreSQL (опционально)
- **Software:** Docker, Docker Compose

### 1. Установка Docker

```bash
# Обновляем систему
sudo apt update && sudo apt upgrade -y

# Устанавливаем Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Добавляем пользователя в группу docker
sudo usermod -aG docker $USER

# Перелогиниваемся
newgrp docker

# Проверяем установку
docker --version
docker compose version
```

### 2. Подготовка директорий

```bash
# Создаем директорию для приложения
sudo mkdir -p /opt/pikabu-bot
sudo chown $USER:$USER /opt/pikabu-bot
cd /opt/pikabu-bot

# Создаем директорию для данных
mkdir -p data logs
```

### 3. Создание .env файла

```bash
cat > .env << 'EOF'
# Telegram Bot
BOT_TOKEN=your_telegram_bot_token
BOT_USERNAME=your_bot_username

# Admin
ADMIN_USER_ID=your_telegram_user_id
ADMIN_ENABLE_NOTIFICATIONS=true
ADMIN_ENABLE_DAILY_DIGEST=false

# Database
DB_NAME=pikabu_bot
DB_USER=pikabu_user
DB_PASSWORD=your_secure_password_here

# Application
SERVER_PORT=8080
APP_MAX_CONCURRENT_DOWNLOADS=5
APP_RATE_LIMIT_MAX_REQUESTS=1000
APP_RATE_LIMIT_WINDOW_HOURS=1

# Database connection (internal)
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/pikabu_bot
SPRING_DATASOURCE_USERNAME=pikabu_user
SPRING_DATASOURCE_PASSWORD=your_secure_password_here
EOF

# Защищаем файл
chmod 600 .env
```

### 4. Создание docker-compose.yml

```bash
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
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${DB_NAME}
      - SPRING_DATASOURCE_USERNAME=${DB_USER}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
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
```

### 5. Настройка firewall (опционально)

```bash
# Устанавливаем ufw
sudo apt install ufw

# Разрешаем SSH
sudo ufw allow ssh

# Разрешаем порт приложения (если нужен внешний доступ)
sudo ufw allow 8080/tcp

# Включаем firewall
sudo ufw enable
```

### 6. Настройка systemd для автозапуска (опционально)

```bash
sudo tee /etc/systemd/system/pikabu-bot.service << 'EOF'
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

[Install]
WantedBy=multi-user.target
EOF

# Включаем и запускаем сервис
sudo systemctl daemon-reload
sudo systemctl enable pikabu-bot.service
sudo systemctl start pikabu-bot.service
```

## Настройка GitHub Secrets

Для автоматического deployment через GitHub Actions нужно настроить secrets.

### 1. Генерация SSH ключа

На вашей локальной машине:

```bash
# Генерируем SSH ключ
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/pikabu_deploy

# Копируем публичный ключ на сервер
ssh-copy-id -i ~/.ssh/pikabu_deploy.pub user@your-server.com

# Проверяем подключение
ssh -i ~/.ssh/pikabu_deploy user@your-server.com
```

### 2. Добавление Secrets в GitHub

Перейдите в Settings → Secrets and variables → Actions → New repository secret

Добавьте следующие secrets:

| Secret Name | Description | Example |
|------------|-------------|---------|
| `DEPLOY_HOST` | IP или hostname сервера | `123.45.67.89` или `bot.example.com` |
| `DEPLOY_USER` | SSH пользователь | `ubuntu` или `deploy` |
| `DEPLOY_SSH_KEY` | Приватный SSH ключ | Содержимое `~/.ssh/pikabu_deploy` |
| `DEPLOY_PORT` | SSH порт (опционально) | `22` (по умолчанию) |

**Как скопировать приватный ключ:**

```bash
cat ~/.ssh/pikabu_deploy
```

Скопируйте ВСЁ содержимое, включая:
```
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

### 3. Настройка GitHub Container Registry

GitHub Container Registry включен по умолчанию. Убедитесь, что:

1. Repository → Settings → Actions → General
2. Workflow permissions: **Read and write permissions** ✓

Образы будут доступны по адресу:
```
ghcr.io/yourusername/pikabu-download-bot:latest
ghcr.io/yourusername/pikabu-download-bot:v1.0.0
```

## CI/CD Pipeline

### Автоматический deployment

Pipeline запускается автоматически при:
- **Push в main/master** → test → build → docker → deploy
- **Создании тега v*** → release → docker build

### Этапы pipeline

1. **Test** - запуск тестов на JDK 17 и 21
2. **Build** - сборка JAR файла
3. **Lint** - проверка code quality
4. **Docker** - сборка и push образа в GHCR
5. **Deploy** - deployment на production сервер

### Процесс deploy

```yaml
1. SSH подключение к серверу
2. Login в GitHub Container Registry
3. Pull нового Docker образа
4. docker-compose down (остановка старой версии)
5. docker-compose up -d (запуск новой версии)
6. Проверка статуса
7. Вывод логов
```

### Мониторинг pipeline

- GitHub Actions → вкладка Actions
- Просмотр логов каждого job
- Email уведомления о failures

## Ручной Deployment

### Первый запуск

```bash
cd /opt/pikabu-bot

# Логинимся в GHCR
echo "YOUR_GITHUB_TOKEN" | docker login ghcr.io -u YOUR_USERNAME --password-stdin

# Pull образа
docker pull ghcr.io/yourusername/pikabu-download-bot:latest

# Запуск
docker-compose up -d

# Проверка логов
docker-compose logs -f bot
```

### Обновление версии

```bash
cd /opt/pikabu-bot

# Pull новой версии
docker-compose pull

# Перезапуск
docker-compose down
docker-compose up -d

# Проверка
docker-compose ps
docker-compose logs --tail=100 bot
```

### Откат к предыдущей версии

```bash
cd /opt/pikabu-bot

# Укажите нужную версию в docker-compose.yml
# Например: image: ghcr.io/yourusername/pikabu-download-bot:v1.0.0

# Перезапуск
docker-compose down
docker-compose up -d
```

## Monitoring & Maintenance

### Просмотр логов

```bash
# Все логи
docker-compose logs -f

# Только бот
docker-compose logs -f bot

# Последние 100 строк
docker-compose logs --tail=100 bot

# С временными метками
docker-compose logs -f -t bot
```

### Проверка здоровья

```bash
# Статус контейнеров
docker-compose ps

# Health check endpoint
curl http://localhost:8080/actuator/health

# Метрики
curl http://localhost:8080/actuator/prometheus | grep bot_
```

### Резервное копирование БД

```bash
# Backup
docker exec pikabu-postgres pg_dump -U pikabu_user pikabu_bot > backup_$(date +%Y%m%d).sql

# Restore
cat backup_20240115.sql | docker exec -i pikabu-postgres psql -U pikabu_user pikabu_bot
```

### Очистка старых образов

```bash
# Удаление неиспользуемых образов
docker image prune -a

# Удаление всего неиспользуемого
docker system prune -a --volumes
```

## Troubleshooting

### Бот не запускается

1. **Проверьте логи:**
```bash
docker-compose logs bot
```

2. **Проверьте .env файл:**
```bash
cat .env | grep BOT_TOKEN
```

3. **Проверьте подключение к БД:**
```bash
docker-compose exec postgres psql -U pikabu_user -d pikabu_bot -c "SELECT 1"
```

### Ошибки deployment в GitHub Actions

1. **SSH connection failed:**
   - Проверьте DEPLOY_HOST, DEPLOY_USER
   - Убедитесь что SSH ключ добавлен на сервер
   - Проверьте firewall (порт 22)

2. **Docker pull failed:**
   - Проверьте что образ существует в GHCR
   - Убедитесь что образ публичный или есть правильные credentials

3. **Permission denied:**
   - Пользователь должен быть в группе docker
   - `sudo usermod -aG docker $USER`

### База данных переполнена

```bash
# Очистка старой истории
docker-compose exec postgres psql -U pikabu_user -d pikabu_bot << 'EOF'
DELETE FROM pikabu_bot.download_history WHERE completed_at < NOW() - INTERVAL '90 days';
DELETE FROM pikabu_bot.video_cache WHERE last_used_at < NOW() - INTERVAL '30 days';
VACUUM FULL;
EOF
```

### Высокая нагрузка

1. **Увеличить ресурсы:**
   - Добавить RAM
   - Увеличить CPU

2. **Оптимизировать настройки:**
```bash
# В .env
APP_MAX_CONCURRENT_DOWNLOADS=3  # Уменьшить
APP_RATE_LIMIT_MAX_REQUESTS=500  # Уменьшить
```

3. **Масштабирование:**
   - Разделить БД на отдельный сервер
   - Использовать Redis для кэша
   - Load balancer + multiple instances

## Security Best Practices

1. **Регулярные обновления:**
```bash
# Обновление системы
sudo apt update && sudo apt upgrade -y

# Обновление Docker образов
docker-compose pull
docker-compose up -d
```

2. **Ограничение доступа:**
   - Используйте firewall (ufw)
   - Закройте ненужные порты
   - Используйте SSH ключи (не пароли)

3. **Мониторинг:**
   - Настройте алерты в Prometheus
   - Проверяйте логи на подозрительную активность
   - Отслеживайте метрики ошибок

4. **Backup:**
   - Регулярные backup БД (daily)
   - Хранение backup в безопасном месте
   - Тестирование восстановления

## Дополнительные настройки

### Nginx reverse proxy (опционально)

```nginx
server {
    listen 80;
    server_name bot.example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /actuator {
        deny all;  # Закрываем метрики от внешнего доступа
    }
}
```

### Логирование в внешнюю систему

Интеграция с ELK, Loki, или Cloudwatch:

```yaml
# docker-compose.yml
services:
  bot:
    logging:
      driver: "fluentd"
      options:
        fluentd-address: localhost:24224
        tag: pikabu-bot
```

## Checklist перед production

- [ ] .env файл настроен и защищен (chmod 600)
- [ ] BOT_TOKEN корректный
- [ ] ADMIN_USER_ID настроен
- [ ] Firewall настроен
- [ ] Docker и Docker Compose установлены
- [ ] GitHub Secrets добавлены
- [ ] SSH ключ добавлен на сервер
- [ ] Backup strategy определена
- [ ] Monitoring настроен
- [ ] Alert rules настроены
- [ ] Health checks работают
- [ ] Логи ротируются
- [ ] Тестовый deployment прошел успешно

🚀 Готово к production!
