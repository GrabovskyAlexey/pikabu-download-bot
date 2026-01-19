# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Проект: Pikabu Download Bot

Telegram-бот для скачивания контента с сайта Pikabu, написанный на Kotlin с использованием Spring Boot.

## Сборка и запуск

### Локальная разработка

```bash
# Сборка проекта
./gradlew build

# Запуск приложения
./gradlew bootRun

# Запуск тестов
./gradlew test

# Запуск интеграционных тестов (с тегом @Tag("integration"))
./gradlew integrationTest

# Генерация отчета о покрытии кода
./gradlew jacocoTestReport
# Отчет находится в build/reports/jacoco/test/html/index.html

# Запуск конкретного теста
./gradlew test --tests "com.pikabu.bot.example.ExampleServiceTest"
```

### Docker

```bash
# Запуск через Docker Compose
docker-compose up -d

# Остановка
docker-compose down

# Просмотр логов
docker-compose logs -f bot
```

## Архитектура

### Технологический стек

- **Kotlin 2.1.0** + **Java 21**
- **Spring Boot 3.5.9** (Web, Data JPA, Actuator)
- **PostgreSQL** с Flyway миграциями
- **Telegram Bot API** (telegrambots-springboot-longpolling-starter 9.2.0)
- **Ktor Client 3.3.1** для HTTP-запросов
- **Jsoup 1.18.3** для парсинга HTML
- **Prometheus** для метрик

### Структура пакетов

```
com.pikabu.bot/
├── PikabuDownloadBotApplication.kt  # Главный класс с @SpringBootApplication + @EnableScheduling
└── example/                          # Примеры кода (удалить при разработке)
```

**Целевая структура согласно плану реализации:**

```
src/main/kotlin/com/pikabu/bot/
├── PikabuDownloadBotApplication.kt
├── config/
│   ├── TelegramBotConfig.kt          # Конфигурация бота
│   ├── DatabaseConfig.kt              # JPA настройки
│   ├── HttpClientConfig.kt            # Ktor client
│   └── RateLimiterConfig.kt           # Rate limiting
├── controller/telegram/
│   ├── TelegramBotController.kt       # Обработка сообщений
│   └── CallbackQueryHandler.kt        # Inline кнопки
├── service/
│   ├── validation/
│   │   └── UrlValidationService.kt    # Валидация pikabu.ru
│   ├── parser/
│   │   ├── VideoParserService.kt      # Координация парсинга
│   │   └── PikabuHtmlParser.kt        # Jsoup парсинг (3 стратегии)
│   ├── download/
│   │   ├── VideoDownloadService.kt    # Ktor streaming
│   │   ├── DownloadOrchestrator.kt    # Координация процесса
│   │   └── StreamingDownloader.kt     # Temp file handling
│   ├── queue/
│   │   ├── QueueService.kt            # CRUD очереди
│   │   └── QueueProcessor.kt          # @Scheduled обработка (5 сек)
│   ├── telegram/
│   │   ├── TelegramSenderService.kt   # Отправка сообщений
│   │   └── MessageUpdaterService.kt   # @Scheduled обновление (7 сек)
│   ├── ratelimit/
│   │   └── RateLimiterService.kt      # Скользящее окно
│   └── admin/
│       ├── AdminNotificationService.kt # Отправка уведомлений
│       └── ErrorMonitoringService.kt   # @Scheduled мониторинг (5 мин)
├── domain/
│   ├── model/
│   │   ├── DownloadRequest.kt
│   │   ├── VideoInfo.kt
│   │   ├── QueueStatus.kt (enum)
│   │   └── UserRateLimit.kt
│   └── exception/
│       ├── VideoNotFoundException.kt
│       ├── DownloadException.kt
│       ├── InvalidUrlException.kt
│       └── RateLimitExceededException.kt
├── entity/
│   ├── DownloadQueueEntity.kt
│   ├── RateLimitEntity.kt
│   ├── DownloadHistoryEntity.kt
│   └── ErrorLogEntity.kt
└── repository/
    ├── DownloadQueueRepository.kt
    ├── RateLimitRepository.kt
    ├── DownloadHistoryRepository.kt
    └── ErrorLogRepository.kt
```

### База данных

- **PostgreSQL 16** (alpine в Docker)
- Миграции через **Flyway** (папка `src/main/resources/db/migration`)
- Hibernate с `ddl-auto: validate` (схема управляется только через Flyway)

**Таблицы:**

#### download_queue
```sql
CREATE TABLE download_queue (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message_id INTEGER NOT NULL,
    video_url VARCHAR(2048) NOT NULL,
    video_title VARCHAR(512),
    status VARCHAR(50) NOT NULL,  -- QUEUED, DOWNLOADING, COMPLETED, FAILED
    position INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_status_created (status, created_at),
    INDEX idx_user_id (user_id)
);
```

#### rate_limits
```sql
CREATE TABLE rate_limits (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP,
    INDEX idx_user_id (user_id)
);
```

#### download_history
```sql
CREATE TABLE download_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    video_url VARCHAR(2048) NOT NULL,
    video_title VARCHAR(512),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    INDEX idx_user_id_completed (user_id, completed_at)
);
```

#### error_log
```sql
CREATE TABLE error_log (
    id BIGSERIAL PRIMARY KEY,
    error_type VARCHAR(100) NOT NULL,  -- PARSING_ERROR, DOWNLOAD_ERROR, SYSTEM_ERROR
    error_message TEXT NOT NULL,
    page_url VARCHAR(2048),
    stack_trace TEXT,
    notified_admin BOOLEAN DEFAULT FALSE,
    occurred_at TIMESTAMP NOT NULL,
    INDEX idx_error_type_occurred (error_type, occurred_at),
    INDEX idx_notified_admin (notified_admin)
);
```

### Конфигурация

Конфигурация находится в `application.yml` и использует переменные окружения:

**Обязательные переменные:**
- `BOT_TOKEN` - токен бота от @BotFather
- `BOT_USERNAME` - username бота
- `ADMIN_USER_ID` - Telegram User ID администратора

**Опциональные (есть значения по умолчанию):**
- `APP_MAX_CONCURRENT_DOWNLOADS` (default: 5)
- `APP_RATE_LIMIT_MAX_REQUESTS` (default: 1000)
- `APP_RATE_LIMIT_WINDOW_HOURS` (default: 1)
- `ADMIN_ENABLE_NOTIFICATIONS` (default: true)
- `ADMIN_ENABLE_DAILY_DIGEST` (default: false)

Пример настройки в `.env` файле смотри в `.env.example`.

### Логирование

Используется **kotlin-logging 7.0.13**:

```kotlin
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class MyService {
    fun doWork() {
        logger.info { "Starting work" }
        logger.debug { "Debug info: ${expensiveOperation()}" } // lazy evaluation
    }
}
```

Подробности в `LIBRARIES.md`.

### Тестирование

Используется **Kotest 6.0.7** + **MockK 1.14.7**:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class MyServiceTest : FunSpec({
    test("should process data") {
        val mock = mockk<Repository>()
        every { mock.find(1) } returns User(1, "Test")

        val result = service.process(1)
        result shouldBe "Test"

        verify { mock.find(1) }
    }
})
```

Интеграционные тесты помечаются `@Tag("integration")` и используют **Testcontainers** для PostgreSQL.

Подробное описание API и примеры в `LIBRARIES.md`.

### Monitoring

- **Spring Actuator** endpoints: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`
- **Prometheus metrics** включены по умолчанию
- Настройка мониторинга ошибок в `application.yml` (секция `app.error-monitoring`)

## Ключевые компоненты системы

### 1. TelegramBotController
**Точка входа для обработки сообщений.**

Процесс обработки:
1. Получить URL от пользователя
2. Проверить rate limit (RateLimiterService)
3. Валидировать URL (UrlValidationService - только pikabu.ru)
4. Парсить видео (VideoParserService)
5. Если 0 видео → "Видео не найдено"
6. Если 1 видео → добавить в очередь (QueueService)
7. Если >1 видео → показать inline кнопки

### 2. PikabuHtmlParser - стратегии парсинга

**Три стратегии поиска видео на странице:**
1. HTML5 `<video>` и `<source>` теги (Jsoup)
2. Data-атрибуты (`data-video-url`)
3. Regex поиск URL в inline JavaScript: `(https?://[^\s"']+\.(mp4|webm|mov|avi))`

**Технические детали:**
- User-Agent для обхода блокировок
- Нормализация относительных URL
- Дедупликация найденных видео
- Поддержка форматов: MP4, WebM, MOV, AVI

### 3. QueueService + QueueProcessor

**QueueService:**
- `addToQueue()` - добавление с автоматической позицией
- `updateStatus()` - QUEUED → DOWNLOADING → COMPLETED/FAILED
- `getQueuePosition()` - текущая позиция
- `getNextPendingRequests()` - получить следующие N запросов
- Архивирование завершенных → download_history

**QueueProcessor:**
- `@Scheduled(fixedDelay = 5000)` - проверка каждые 5 секунд
- Запуск новых загрузок при наличии свободных слотов (макс. 5)
- Использование корутин для параллельной загрузки

### 4. VideoDownloadService + StreamingDownloader

**VideoDownloadService:**
- Ktor HttpClient для streaming загрузки
- `Flow<ByteArray>` для потоковой передачи
- Контроль размера во время загрузки (500 МБ лимит)
- Timeout: 5 минут
- Retry логика: 3 попытки с exponential backoff (1s, 2s, 3s)

**StreamingDownloader - процесс:**
1. Создать временный файл (Files.createTempFile)
2. Записать поток данных в файл с контролем размера
3. Отправить в Telegram как video
4. Немедленно удалить файл (finally блок)

### 5. MessageUpdaterService

**@Scheduled обновление статуса в Telegram:**
- `@Scheduled(fixedDelay = 7000)` - каждые 7 секунд
- Найти все QUEUED запросы
- Обновить: "Ваш запрос в очереди, позиция: N"
- При DOWNLOADING → "Загружается видео..."
- При завершении → удалить сообщение, отправить видео

### 6. RateLimiterService

**Скользящее временное окно:**
- `max-requests`: 1000 (настраиваемый)
- `window-hours`: 1 час (настраиваемый)
- Автоматический сброс при истечении окна
- Хранение счетчиков в PostgreSQL

### 7. ErrorMonitoringService + AdminNotificationService

**Мониторинг критических ошибок:**

`@Scheduled(fixedDelay = 300000)` - проверка каждые 5 минут

**Триггеры уведомлений:**
1. **Ошибки парсинга**: 5+ ошибок за 10 минут
   - Возможно изменилась структура HTML Pikabu
   - Сообщение: "⚠️ Обнаружено 5 ошибок парсинга за 10 минут. Возможно изменилась структура страниц Pikabu."

2. **Ошибки загрузки**: 10+ ошибок за 15 минут
   - Проблемы с сетью или блокировка
   - Сообщение: "⚠️ Обнаружено 10 ошибок загрузки за 15 минут. Проверьте доступность Pikabu."

3. **Системные ошибки**: немедленно
   - БД недоступна, OOM, etc.
   - Сообщение: "🚨 КРИТИЧЕСКАЯ ОШИБКА: {message}"

4. **Дневной дайджест** (опционально):
   - Статистика: загружено видео, ошибок, активных пользователей

**Throttling:** флаг `notified_admin` предотвращает спам уведомлений.

## Решения сложных задач

### Streaming без сохранения на диск
- Ktor `ByteReadChannel` для потоковой загрузки
- Временный файл в `/tmp` с автоочисткой
- `finally` блок для гарантированного удаления
- Telegram API требует файл (полностью избежать диска нельзя)

### Контроль размера во время загрузки
```kotlin
var downloadedBytes = 0L
videoFlow.collect { bytes ->
    downloadedBytes += bytes.size
    if (downloadedBytes > MAX_SIZE_BYTES) {
        throw DownloadException("Превышен лимит 500 МБ")
    }
    emit(bytes)
}
```

### Парсинг динамического контента Pikabu
- Комбинация 3 стратегий парсинга
- Regex поиск URL в inline скриптах
- Возможность добавления yt-dlp как fallback

### Обновление сообщений без спама API
- Обновление каждые 7 секунд (не чаще)
- Batch обработка всех QUEUED запросов
- Try-catch для защиты от ошибок API

### Выбор видео при нескольких на странице
- InlineKeyboardMarkup с кнопками
- CallbackQuery: `select_video:<page_url>:<video_url>`
- CallbackQueryHandler обрабатывает выбор

## Особенности разработки

### Kotlin Coroutines

Проект использует coroutines (`kotlinx-coroutines-core 1.9.0`), хотя Spring Boot в основном работает синхронно. Ktor Client и потенциально обработка сообщений бота могут использовать suspend функции.

### Rate Limiting

Настроено ограничение запросов через конфигурацию `app.rate-limit` для защиты от злоупотреблений.

### Download Limits

Настроены лимиты на размер скачиваемых файлов (500 MB), таймауты (5 минут) и количество попыток (3) в секции `app.download`.

## Важные команды Gradle

```bash
# Обновление зависимостей
./gradlew dependencies --refresh-dependencies

# Проверка устаревших зависимостей
./gradlew dependencyUpdates

# Очистка build директории
./gradlew clean

# Запуск с профилем (если настроены)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Этапы реализации (согласно плану)

Проект реализуется в 11 фаз согласно детальному плану в `C:\Users\crazy\.claude\plans\witty-stirring-ladybug.md`:

### Phase 1: Базовая инфраструктура ✅
- build.gradle.kts, settings.gradle.kts
- PikabuDownloadBotApplication.kt
- application.yml с переменными окружения
- Dockerfile (multi-stage build)
- docker-compose.yml (bot + PostgreSQL)

### Phase 2: Telegram Integration
- config/TelegramBotConfig.kt
- controller/telegram/TelegramBotController.kt
- controller/telegram/CallbackQueryHandler.kt
- service/telegram/TelegramSenderService.kt

### Phase 3: URL Validation & Parsing
- service/validation/UrlValidationService.kt
- service/parser/VideoParserService.kt + PikabuHtmlParser.kt
- domain/model/VideoInfo.kt
- domain/exception/*

### Phase 4: Database Layer
- entity/* (все 4 сущности)
- repository/* (все 4 репозитория)
- Flyway миграции V1-V4

### Phase 5: Queue Management
- service/queue/QueueService.kt + QueueProcessor.kt
- service/telegram/MessageUpdaterService.kt
- domain/model/QueueStatus.kt

### Phase 6: Download System
- config/HttpClientConfig.kt
- service/download/VideoDownloadService.kt
- service/download/StreamingDownloader.kt + DownloadOrchestrator.kt

### Phase 7: Rate Limiting
- config/RateLimiterConfig.kt
- service/ratelimit/RateLimiterService.kt

### Phase 8: Admin Notification System
- config/AdminConfig.kt
- service/admin/AdminNotificationService.kt
- service/admin/ErrorMonitoringService.kt

### Phase 9: Testing
- Unit тесты для всех сервисов
- Integration тесты с Testcontainers
- E2E тесты

### Phase 10: CI/CD
- .github/workflows/ci-cd.yml

### Phase 11: Monitoring & Optimization
- Spring Actuator (уже настроен)
- Кастомные метрики
- Prometheus endpoint

## Приоритеты при реализации

**Приоритет 1** (ядро системы):
1. build.gradle.kts
2. PikabuDownloadBotApplication.kt
3. TelegramBotController.kt
4. PikabuHtmlParser.kt
5. VideoDownloadService.kt
6. QueueService.kt + QueueProcessor.kt
7. Flyway миграции (V1, V2, V3, V4)

**Приоритет 2** (важная логика):
- UrlValidationService.kt
- RateLimiterService.kt
- MessageUpdaterService.kt
- TelegramSenderService.kt
- DownloadOrchestrator.kt
- CallbackQueryHandler.kt
- AdminNotificationService.kt + ErrorMonitoringService.kt

**Приоритет 3** (инфраструктура):
- application.yml
- Dockerfile
- docker-compose.yml
- CI/CD pipeline

## Тестовые сценарии

### Сценарий 1: Успешная загрузка одного видео
1. Отправить валидную ссылку pikabu.ru с видео
2. Ожидать: "Загружается видео..."
3. Ожидать: получение видео файла

### Сценарий 2: Несколько видео на странице
1. Отправить ссылку со множественными видео
2. Ожидать: inline кнопки "Видео 1", "Видео 2"
3. Нажать кнопку
4. Ожидать: загрузку выбранного видео

### Сценарий 3: Очередь загрузок
1. Отправить 10 ссылок быстро
2. Первые 5 начнут загружаться
3. Для 6-10: "Позиция в очереди: N"
4. Периодическое обновление позиции
5. Все 10 видео загрузятся по очереди

### Сценарий 4: Ошибки
1. Не pikabu.ru → ошибка валидации
2. pikabu.ru без видео → "Видео не найдено"
3. Превышен rate limit → "Превышен лимит запросов"

### Сценарий 5: Уведомления админа
1. 6 ошибок парсинга подряд
2. Сохранение в error_log
3. Через 5 минут админ получит уведомление
4. Флаг notified_admin = true (throttling)

## База знаний

Детальная информация об используемых библиотеках (kotlin-logging, Kotest, MockK) с примерами кода находится в `LIBRARIES.md`.

Полный детальный план реализации с техническими решениями находится в `C:\Users\crazy\.claude\plans\witty-stirring-ladybug.md`.
