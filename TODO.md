# TODO - Pikabu Download Bot

Прогресс реализации проекта по фазам из плана `C:\Users\crazy\.claude\plans\witty-stirring-ladybug.md`

## Статус

- ✅ Завершено: 5/11 фаз
- 🔄 В работе: Phase 6
- ⏳ Осталось: 5 фаз

---

## ✅ Phase 1: Базовая инфраструктура

**Статус:** Завершена

**Реализовано:**
- ✅ build.gradle.kts с зависимостями (Spring Boot 3.5.9, Kotlin 2.1.0, Telegram Bots 9.2.0)
- ✅ settings.gradle.kts
- ✅ PikabuDownloadBotApplication.kt с @EnableScheduling
- ✅ application.yml с переменными окружения
- ✅ Dockerfile (multi-stage build)
- ✅ docker-compose.yml (bot + PostgreSQL)
- ✅ .env.example

---

## ✅ Phase 2: Telegram Integration

**Статус:** Завершена

**Реализовано:**
- ✅ config/TelegramBotConfig.kt
- ✅ config/TelegramClientConfig.kt (регистрация бота)
- ✅ controller/telegram/TelegramBotController.kt (обработка сообщений, команды /start, /help)
- ✅ controller/telegram/CallbackQueryHandler.kt (inline кнопки)
- ✅ service/telegram/TelegramSenderService.kt (отправка сообщений, видео, inline клавиатур)

**Файлы:**
- `src/main/kotlin/com/pikabu/bot/config/TelegramBotConfig.kt`
- `src/main/kotlin/com/pikabu/bot/config/TelegramClientConfig.kt`
- `src/main/kotlin/com/pikabu/bot/controller/telegram/TelegramBotController.kt`
- `src/main/kotlin/com/pikabu/bot/controller/telegram/CallbackQueryHandler.kt`
- `src/main/kotlin/com/pikabu/bot/service/telegram/TelegramSenderService.kt`

---

## ✅ Phase 3: URL Validation & Parsing

**Статус:** Завершена

**Реализовано:**
- ✅ domain/model/VideoInfo.kt (модель видео)
- ✅ domain/model/VideoFormat.kt (enum форматов)
- ✅ domain/exception/* (4 класса исключений)
- ✅ service/validation/UrlValidationService.kt (валидация pikabu.ru)
- ✅ service/parser/VideoParserService.kt (координация парсинга)
- ✅ service/parser/PikabuHtmlParser.kt (3 стратегии парсинга)
- ✅ config/HttpClientConfig.kt (Ktor client)
- ✅ Интеграция в TelegramBotController

**Стратегии парсинга:**
1. HTML5 `<video>` и `<source>` теги (Jsoup)
2. Data-атрибуты (data-video-url, data-src)
3. Regex поиск URL в inline JavaScript

**Файлы:**
- `src/main/kotlin/com/pikabu/bot/domain/model/VideoInfo.kt`
- `src/main/kotlin/com/pikabu/bot/domain/exception/*.kt`
- `src/main/kotlin/com/pikabu/bot/service/validation/UrlValidationService.kt`
- `src/main/kotlin/com/pikabu/bot/service/parser/VideoParserService.kt`
- `src/main/kotlin/com/pikabu/bot/service/parser/PikabuHtmlParser.kt`
- `src/main/kotlin/com/pikabu/bot/config/HttpClientConfig.kt`

---

## ✅ Phase 4: Database Layer

**Статус:** Завершена

**Реализовано:**
- ✅ domain/model/QueueStatus.kt (enum статусов)
- ✅ entity/DownloadQueueEntity.kt
- ✅ entity/RateLimitEntity.kt
- ✅ entity/DownloadHistoryEntity.kt
- ✅ entity/ErrorLogEntity.kt
- ✅ repository/DownloadQueueRepository.kt
- ✅ repository/RateLimitRepository.kt
- ✅ repository/DownloadHistoryRepository.kt
- ✅ repository/ErrorLogRepository.kt
- ✅ Flyway миграции V1-V4

**Таблицы:**
- `download_queue` - очередь загрузок
- `rate_limits` - лимиты запросов
- `download_history` - история загрузок
- `error_log` - лог ошибок

**Файлы:**
- `src/main/kotlin/com/pikabu/bot/domain/model/QueueStatus.kt`
- `src/main/kotlin/com/pikabu/bot/entity/*.kt` (4 файла)
- `src/main/kotlin/com/pikabu/bot/repository/*.kt` (4 файла)
- `src/main/resources/db/migration/V1__create_download_queue_table.sql`
- `src/main/resources/db/migration/V2__create_rate_limit_table.sql`
- `src/main/resources/db/migration/V3__create_download_history_table.sql`
- `src/main/resources/db/migration/V4__create_error_log_table.sql`

---

## ✅ Phase 5: Queue Management

**Статус:** Завершена

**Реализовано:**
- ✅ service/queue/QueueService.kt
  - addToQueue() - добавление в очередь с автоматической позицией
  - updateStatus() - обновление статуса (QUEUED → DOWNLOADING → COMPLETED/FAILED)
  - getQueuePosition() - текущая позиция
  - getNextPendingRequests() - получить следующие N запросов
  - archiveToHistory() - архивирование в download_history
  - recalculatePositions() - перерасчет позиций
- ✅ service/queue/QueueProcessor.kt
  - @Scheduled(fixedDelay = 5000) - проверка каждые 5 секунд
  - Запуск новых загрузок (макс 5 одновременно)
  - Использование корутин для параллельной обработки
  - getQueueStats() - статистика очереди
- ✅ service/telegram/MessageUpdaterService.kt
  - @Scheduled(fixedDelay = 7000) - обновление каждые 7 секунд
  - Обновление сообщений о позиции в очереди
  - "Загружается видео..." при DOWNLOADING
  - sendQueueAddedMessage() - начальное сообщение
- ✅ Интеграция в TelegramBotController (метод addVideoToQueue)
- ✅ Интеграция в CallbackQueryHandler (обработка выбора видео)

**Файлы:**
- `src/main/kotlin/com/pikabu/bot/service/queue/QueueService.kt`
- `src/main/kotlin/com/pikabu/bot/service/queue/QueueProcessor.kt`
- `src/main/kotlin/com/pikabu/bot/service/telegram/MessageUpdaterService.kt`

**Примечание:** QueueProcessor содержит TODO для Phase 6 (реальная загрузка видео). Пока используется заглушка с симуляцией.

---

## 🔄 Phase 6: Download System

**Статус:** В работе

**Нужно реализовать:**
- service/download/VideoDownloadService.kt
  - Ktor streaming загрузка
  - Flow<ByteArray>
  - Контроль размера (500 МБ)
  - Timeout 5 минут
  - Retry логика (3 попытки с exponential backoff)
- service/download/StreamingDownloader.kt
  - Временный файл в /tmp
  - Запись потока
  - Отправка в Telegram
  - finally блок для удаления
- service/download/DownloadOrchestrator.kt
  - Координация процесса загрузки

---

## ⏳ Phase 7: Rate Limiting

**Статус:** Ожидает

**Нужно реализовать:**
- config/RateLimiterConfig.kt
- service/ratelimit/RateLimiterService.kt
  - Скользящее временное окно
  - Настраиваемые лимиты (1000 запросов/час)
  - Интеграция в TelegramBotController
- domain/exception/RateLimitExceededException.kt (уже создано)

---

## ⏳ Phase 8: Admin Notification System

**Статус:** Ожидает

**Нужно реализовать:**
- config/AdminConfig.kt
- service/admin/AdminNotificationService.kt
  - Отправка уведомлений админу
  - Форматирование сообщений
  - Throttling уведомлений
- service/admin/ErrorMonitoringService.kt
  - @Scheduled(fixedDelay = 300000) - каждые 5 минут
  - Анализ error_log
  - Группировка ошибок
  - Триггеры: 5+ ошибок парсинга/10 минут, 10+ ошибок загрузки/15 минут
- Интеграция в VideoParserService и VideoDownloadService

---

## ⏳ Phase 9: Testing

**Статус:** Ожидает

**Нужно написать:**
- Unit тесты:
  - UrlValidationServiceTest.kt
  - VideoParserServiceTest.kt
  - VideoDownloadServiceTest.kt
  - QueueServiceTest.kt
  - RateLimiterServiceTest.kt
  - AdminNotificationServiceTest.kt
  - ErrorMonitoringServiceTest.kt
- Integration тесты:
  - TelegramBotIntegrationTest.kt
  - QueueProcessingIntegrationTest.kt
  - DatabaseIntegrationTest.kt
- Testcontainers:
  - PostgresTestContainer.kt
- E2E тесты для основных сценариев

---

## ⏳ Phase 10: CI/CD

**Статус:** Ожидает

**Нужно реализовать:**
- .github/workflows/ci-cd.yml
  - Test Job: checkout → JDK 21 → tests → coverage
  - Build Job: Docker buildx → GHCR → push image
  - Deploy Job (опционально): SSH → docker compose pull → restart

---

## ⏳ Phase 11: Monitoring & Optimization

**Статус:** Ожидает

**Нужно добавить:**
- Кастомные метрики:
  - Количество загрузок
  - Размер очереди
  - Успешные/неудачные загрузки
- Prometheus endpoint (уже настроен в Spring Actuator)
- Health checks для БД и бота
- Логирование (уже настроено kotlin-logging)

---

## Следующие шаги

1. **Сейчас:** Завершить Phase 5 (Queue Management)
2. **Потом:** Phase 6 (Download System) - ключевой функционал
3. **Затем:** Phase 7 (Rate Limiting)
4. **Далее:** Phase 8 (Admin Notifications)
5. **Тестирование:** Phase 9
6. **Деплой:** Phase 10-11

---

**Последнее обновление:** Phase 5 завершена, начата Phase 6
**Следующая цель:** Реализовать VideoDownloadService, StreamingDownloader и DownloadOrchestrator
