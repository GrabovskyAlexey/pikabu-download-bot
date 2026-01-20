# 🤖 Pikabu Download Bot

Telegram бот для скачивания видео с сайта Pikabu.ru.

[![CI/CD](https://github.com/yourusername/pikabu-download-bot/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/yourusername/pikabu-download-bot/actions/workflows/ci-cd.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.25-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg?logo=spring-boot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## 📋 Оглавление

- [Возможности](#-возможности)
- [Технологический стек](#-технологический-стек)
- [Архитектура](#-архитектура)
- [Быстрый старт](#-быстрый-старт)
- [CI/CD](#-cicd)
- [Deployment](#-deployment)
- [API](#-api)
- [Разработка](#-разработка)
- [Тестирование](#-тестирование)

## ✨ Возможности

- 📹 **Скачивание видео** с Pikabu.ru
- 🔄 **Умная очередь** с автоматической обработкой
- 💾 **Кэширование** file_id для мгновенной повторной отправки
- ⚡ **Rate limiting** для защиты от перегрузки
- 📊 **Мониторинг ошибок** с уведомлениями админу
- 🗄️ **PostgreSQL** для хранения истории и очереди
- 🐳 **Docker** ready для простого deployment
- 🔒 **Безопасность** - streaming загрузка без промежуточного хранения

## 🛠 Технологический стек

- **Язык:** Kotlin 1.9.25
- **Фреймворк:** Spring Boot 3.4.1
- **База данных:** PostgreSQL 16
- **HTTP Client:** Ktor 2.3.5
- **Telegram API:** TelegramBots 7.10.0
- **HTML Parser:** Jsoup 1.18.3
- **Build Tool:** Gradle 8.11.1
- **Container:** Docker + Docker Compose

## 🏗 Архитектура

```
┌─────────────────┐
│  Telegram User  │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│  TelegramBotController  │◄─── Получение сообщений
└────────┬────────────────┘
         │
         ├──► UrlValidationService ──────► Валидация URL
         │
         ├──► VideoParserService ─────────► Парсинг страницы (Jsoup)
         │
         ├──► VideoCacheService ──────────► Проверка cache (PostgreSQL)
         │
         └──► QueueService ────────────────► Добавление в очередь
                     │
                     ▼
         ┌──────────────────────┐
         │   QueueProcessor     │◄──── Периодическая обработка (@Scheduled)
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │ DownloadOrchestrator │◄──── Координация загрузки
         └──────────┬───────────┘
                    │
                    ├──► StreamingDownloader ────► Загрузка видео (Ktor)
                    │
                    ├──► TelegramSenderService ──► Отправка в Telegram
                    │
                    └──► VideoCacheService ──────► Сохранение file_id
```

### Основные компоненты

#### 1. Controller Layer
- `TelegramBotController` - обработка входящих сообщений
- `CallbackQueryHandler` - обработка inline кнопок

#### 2. Service Layer
- `VideoParserService` - парсинг HTML страниц Pikabu
- `VideoDownloadService` - загрузка видео с retry логикой
- `QueueService` - управление очередью загрузок
- `VideoCacheService` - кэширование file_id
- `RateLimiterService` - rate limiting по пользователям

#### 3. Repository Layer
- JPA repositories для работы с PostgreSQL

#### 4. Domain Layer
- Entity классы для БД
- Exception классы
- Domain модели

## 🚀 Быстрый старт

### Требования

- **Production:** Docker + Docker Compose (рекомендуется)
- **Development:** Java 17+, PostgreSQL 16, Telegram Bot Token

### 🔴 Production Deployment (5 минут)

**Следуйте чеклисту:** [PRODUCTION_CHECKLIST.md](PRODUCTION_CHECKLIST.md)

#### Вариант 1: С PostgreSQL в Docker (рекомендуется)

```bash
# На production сервере:
curl -o setup.sh https://raw.githubusercontent.com/yourusername/pikabu-download-bot/main/scripts/setup-production.sh
chmod +x setup.sh
./setup.sh

# Редактируйте .env файл
cd /opt/pikabu-bot
nano .env  # Укажите BOT_TOKEN, ADMIN_USER_ID, пароли

# Запустите
docker compose up -d
```

#### Вариант 2: С существующей PostgreSQL

Если PostgreSQL уже установлен на сервере:

```bash
# Создайте БД и схему
sudo -u postgres psql << 'EOF'
CREATE DATABASE pikabu_bot;
\c pikabu_bot
CREATE USER pikabu_user WITH PASSWORD 'password';
CREATE SCHEMA IF NOT EXISTS pikabu_bot;
GRANT ALL PRIVILEGES ON DATABASE pikabu_bot TO pikabu_user;
GRANT ALL ON SCHEMA pikabu_bot TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON TABLES TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON SEQUENCES TO pikabu_user;
EOF

# Используйте конфиг для внешней БД
cd /opt/pikabu-bot
cp docker-compose.prod-external-db.yml docker-compose.yml
cp .env.production-external-db .env
nano .env  # Настройте SPRING_DATASOURCE_URL

# Запустите
docker compose up -d
```

📚 **Подробнее:** [docs/EXTERNAL_DATABASE.md](docs/EXTERNAL_DATABASE.md)

✨ **Готово!** Автоматический deployment через GitHub Actions настроится отдельно.

### 🟢 Development

### Локальный запуск

1. **Клонируйте репозиторий:**
```bash
git clone https://github.com/yourusername/pikabu-download-bot.git
cd pikabu-download-bot
```

2. **Создайте .env файл:**
```bash
cp .env.example .env
# Отредактируйте .env и укажите ваши данные
```

3. **Запустите PostgreSQL:**
```bash
docker run -d \
  --name pikabu-postgres \
  -e POSTGRES_DB=pikabu_bot \
  -e POSTGRES_USER=pikabu_user \
  -e POSTGRES_PASSWORD=your_password \
  -p 5432:5432 \
  postgres:16-alpine
```

4. **Запустите приложение:**
```bash
./gradlew bootRun
```

### Docker Compose (рекомендуется)

```bash
# Настройте .env файл
cp .env.example .env

# Запустите все сервисы
docker-compose up -d

# Просмотр логов
docker-compose logs -f bot

# Остановка
docker-compose down
```

## 🔄 CI/CD

Проект использует GitHub Actions для автоматизации:

### Workflows

1. **CI/CD Pipeline** (`.github/workflows/ci-cd.yml`)
   - Запускается при push/PR в main/master/develop
   - Тестирование на JDK 17 и 21
   - Сборка JAR артефакта
   - Code quality check (ktlint)

2. **Release** (`.github/workflows/release.yml`)
   - Автоматический release при создании тега
   - Публикация JAR файла

3. **Security Check** (`.github/workflows/security.yml`)
   - Еженедельная проверка зависимостей
   - Сканирование на уязвимости

### Создание релиза

```bash
# Создайте тег с версией
git tag v1.0.0
git push origin v1.0.0

# GitHub Actions автоматически создаст release с JAR
```

Подробности в [.github/workflows/README.md](.github/workflows/README.md)

## 🚀 Deployment

### Автоматический Deployment (CI/CD)

Проект настроен для автоматического deployment через GitHub Actions:

1. **Push в main/master** → автоматические тесты → сборка Docker образа → deploy на production
2. **Создание тега v*** → release с JAR + Docker образ

**Docker образы доступны в GitHub Container Registry:**
```bash
docker pull ghcr.io/yourusername/pikabu-download-bot:latest
docker pull ghcr.io/yourusername/pikabu-download-bot:v1.0.0
```

### Настройка автоматического deployment

1. **Настройте GitHub Secrets** (Settings → Secrets → Actions):
   - `DEPLOY_HOST` - IP/hostname сервера
   - `DEPLOY_USER` - SSH пользователь
   - `DEPLOY_SSH_KEY` - приватный SSH ключ
   - `DEPLOY_PORT` - SSH порт (опционально, по умолчанию 22)

2. **Подготовьте production сервер:**
```bash
# Установите Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Создайте директорию
sudo mkdir -p /opt/pikabu-bot
cd /opt/pikabu-bot

# Создайте .env и docker-compose.yml
# (см. docs/DEPLOYMENT.md)
```

3. **Push в main** - deployment произойдет автоматически!

### Ручной Deployment

```bash
# Pull Docker образа
docker pull ghcr.io/yourusername/pikabu-download-bot:latest

# Запуск с docker-compose
docker-compose up -d

# Проверка логов
docker-compose logs -f bot
```

📚 **Полная инструкция:** [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

### Environment Variables

Основные переменные окружения:

| Переменная | Описание | По умолчанию |
|-----------|----------|--------------|
| `BOT_TOKEN` | Telegram Bot Token | - (обязательно) |
| `BOT_USERNAME` | Username бота | - (обязательно) |
| `ADMIN_USER_ID` | Telegram ID админа | 0 |
| `DB_PASSWORD` | Пароль PostgreSQL | - (обязательно) |
| `APP_MAX_CONCURRENT_DOWNLOADS` | Макс. одновременных загрузок | 5 |
| `APP_RATE_LIMIT_MAX_REQUESTS` | Лимит запросов в час | 1000 |

Полный список в `.env.example`

## 📡 API

### Telegram команды

- `/start` - Начало работы с ботом
- `/help` - Справка по использованию

### Inline кнопки

При наличии нескольких видео на странице, бот предложит выбрать нужное через inline-кнопки.

### Spring Boot Actuator

Эндпоинты для мониторинга (доступны на порту 8080):

- `/actuator/health` - Health check
- `/actuator/info` - Информация о приложении
- `/actuator/metrics` - Метрики

## 👨‍💻 Разработка

### Настройка IDE

Рекомендуется IntelliJ IDEA с Kotlin plugin.

1. Импортируйте проект как Gradle проект
2. Установите Code Style: Settings → Editor → Code Style → Kotlin → Set from... → Kotlin style guide

### Структура проекта

```
src/main/kotlin/com/pikabu/bot/
├── config/              # Конфигурация Spring
├── controller/          # Telegram обработчики
├── domain/              # Domain модели и exceptions
├── entity/              # JPA entities
├── repository/          # JPA repositories
└── service/             # Бизнес-логика
    ├── admin/          # Админ-уведомления
    ├── cache/          # Кэширование
    ├── download/       # Загрузка видео
    ├── parser/         # Парсинг HTML
    ├── queue/          # Очередь
    ├── ratelimit/      # Rate limiting
    ├── telegram/       # Telegram API
    └── validation/     # Валидация
```

### Code Style

Проект использует ktlint для проверки стиля кода:

```bash
# Проверка
./gradlew ktlintCheck

# Автоисправление
./gradlew ktlintFormat
```

## 🧪 Тестирование

### Unit тесты

```bash
# Запуск всех тестов
./gradlew test

# Запуск конкретного теста
./gradlew test --tests "UrlValidationServiceTest"

# С отчетом покрытия
./gradlew test jacocoTestReport
```

### Тестовое покрытие

Отчет о покрытии: `build/reports/jacoco/test/html/index.html`

Текущее покрытие основных сервисов: ~80%

### Тестирование Docker образа

```bash
# Сборка образа
docker build -t pikabu-bot:test .

# Запуск
docker run --env-file .env pikabu-bot:test
```

## 📊 Мониторинг

### Логирование

- **INFO** - важные бизнес-события (добавление в очередь, отправка видео)
- **DEBUG** - технические детали (кэш hit/miss, парсинг)
- **ERROR** - ошибки с уведомлением админа

### Метрики

Spring Boot Actuator предоставляет метрики:
- JVM (heap, threads, GC)
- HTTP requests
- Database connections
- Custom metrics (можно добавить)

## 🤝 Contributing

1. Fork репозиторий
2. Создайте feature branch (`git checkout -b feature/amazing-feature`)
3. Commit изменения (`git commit -m 'Add amazing feature'`)
4. Push в branch (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📝 License

Этот проект лицензирован под MIT License - см. [LICENSE](LICENSE) для деталей.

## 👤 Автор

Ваше имя - [@yourusername](https://github.com/yourusername)

## 🙏 Благодарности

- [Spring Framework](https://spring.io/)
- [Kotlin](https://kotlinlang.org/)
- [TelegramBots](https://github.com/rubenlagus/TelegramBots)
- [Ktor](https://ktor.io/)
- [Jsoup](https://jsoup.org/)
