# 📊 Метрики и мониторинг

Проект использует **Micrometer** с **Prometheus** для сбора и экспорта метрик.

## Доступ к метрикам

### Prometheus Endpoint

```
GET http://localhost:8080/actuator/prometheus
```

Возвращает метрики в формате Prometheus для scraping.

### Metrics Endpoint (JSON)

```
GET http://localhost:8080/actuator/metrics
```

Список всех доступных метрик в JSON формате.

```
GET http://localhost:8080/actuator/metrics/{metric.name}
```

Детали конкретной метрики.

### Health Endpoint

```
GET http://localhost:8080/actuator/health
```

Статус здоровья приложения и его компонентов.

## Кастомные метрики

### Счетчики загрузок (Counters)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `bot.downloads.successful` | Counter | Количество успешных загрузок видео |
| `bot.downloads.failed` | Counter | Количество неудачных загрузок видео |

**Пример запроса:**
```
GET http://localhost:8080/actuator/metrics/bot.downloads.successful
```

### Метрики кэша (Counters)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `bot.cache.hits` | Counter | Количество попаданий в кэш (video file_id) |
| `bot.cache.misses` | Counter | Количество промахов кэша |

**Cache Hit Rate** можно вычислить как:
```
cache_hit_rate = hits / (hits + misses)
```

### Метрики ошибок (Counters)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `bot.errors.parsing` | Counter | Количество ошибок парсинга HTML |
| `bot.errors.download` | Counter | Количество ошибок загрузки видео |
| `bot.errors.ratelimit` | Counter | Количество превышений rate limit |

### Метрики очереди (Gauges)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `bot.queue.size` | Gauge | Текущее количество видео в очереди на загрузку |
| `bot.downloads.active` | Gauge | Количество активных загрузок в данный момент |

**Gauge** - мгновенное значение, которое может увеличиваться и уменьшаться.

### Таймеры производительности (Timers)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `bot.downloads.duration` | Timer | Время загрузки видео |
| `bot.parsing.duration` | Timer | Время парсинга HTML страницы |

**Timer** предоставляет:
- `count` - количество измерений
- `sum` - сумма всех времен
- `max` - максимальное время
- Процентили (p50, p95, p99)

### Пользовательские метрики (Counters)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `bot.users.unique` | Counter | Общее количество уникальных пользователей |

## Стандартные метрики Spring Boot

### JVM Метрики

- `jvm.memory.used` - используемая память
- `jvm.memory.max` - максимальная память
- `jvm.threads.live` - количество живых потоков
- `jvm.gc.pause` - паузы GC

### System Метрики

- `system.cpu.usage` - использование CPU
- `system.load.average.1m` - средняя нагрузка за 1 минуту
- `system.cpu.count` - количество CPU

### HTTP Метрики

- `http.server.requests` - HTTP запросы к Spring Boot
- Включает теги: uri, method, status, outcome

### Database Метрики

- `hikaricp.connections.active` - активные DB соединения
- `hikaricp.connections.idle` - idle соединения
- `hikaricp.connections.pending` - ожидающие соединения

## Настройка Prometheus

### 1. Установка Prometheus

**Docker Compose:**

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'

volumes:
  prometheus-data:
```

### 2. Конфигурация Prometheus (prometheus.yml)

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'pikabu-bot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['bot:8080']  # Если в Docker Compose
        # или
        # - targets: ['localhost:8080']  # Если локально
```

### 3. Запуск

```bash
docker-compose up -d prometheus
```

Prometheus UI: http://localhost:9090

## Настройка Grafana

### 1. Установка Grafana

```yaml
services:
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana

volumes:
  grafana-data:
```

### 2. Добавление Data Source

1. Откройте Grafana: http://localhost:3000 (admin/admin)
2. Configuration → Data Sources → Add data source
3. Выберите Prometheus
4. URL: `http://prometheus:9090`
5. Save & Test

### 3. Импорт дашборда

#### Дашборд для Spring Boot (ID: 11378)

1. Dashboards → Import
2. Введите ID: 11378
3. Выберите Prometheus data source
4. Import

#### Кастомный дашборд для Pikabu Bot

Создайте новый dashboard с панелями:

**Panel 1: Успешные vs Неудачные загрузки**
```promql
rate(bot_downloads_successful_total[5m])
rate(bot_downloads_failed_total[5m])
```

**Panel 2: Cache Hit Rate**
```promql
rate(bot_cache_hits_total[5m]) / (rate(bot_cache_hits_total[5m]) + rate(bot_cache_misses_total[5m]))
```

**Panel 3: Размер очереди**
```promql
bot_queue_size
```

**Panel 4: Активные загрузки**
```promql
bot_downloads_active
```

**Panel 5: Среднее время загрузки (p95)**
```promql
histogram_quantile(0.95, rate(bot_downloads_duration_seconds_bucket[5m]))
```

**Panel 6: Rate limit ошибки**
```promql
rate(bot_errors_ratelimit_total[5m])
```

## Алерты (Alerting)

### Пример alert rules (alert.rules.yml)

```yaml
groups:
  - name: pikabu_bot_alerts
    interval: 30s
    rules:
      # Высокий процент ошибок загрузки
      - alert: HighDownloadFailureRate
        expr: |
          rate(bot_downloads_failed_total[5m])
          /
          (rate(bot_downloads_successful_total[5m]) + rate(bot_downloads_failed_total[5m]))
          > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High download failure rate"
          description: "More than 10% of downloads are failing (current: {{ $value }})"

      # Очередь слишком большая
      - alert: LargeQueue
        expr: bot_queue_size > 100
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Download queue is too large"
          description: "Queue size is {{ $value }} videos"

      # Слишком много ошибок парсинга
      - alert: HighParsingErrorRate
        expr: rate(bot_errors_parsing_total[5m]) > 1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High parsing error rate"
          description: "Parsing errors: {{ $value }} per second"

      # Высокое использование памяти
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High JVM memory usage"
          description: "Heap memory usage is at {{ $value | humanizePercentage }}"

      # DB connection pool исчерпан
      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active >= hikaricp_connections_max
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool exhausted"
          description: "All database connections are in use"
```

### Добавление alert rules в Prometheus

В `prometheus.yml`:

```yaml
rule_files:
  - "alert.rules.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

## Примеры PromQL запросов

### Общая статистика

```promql
# Общее количество успешных загрузок
bot_downloads_successful_total

# Rate успешных загрузок за последние 5 минут
rate(bot_downloads_successful_total[5m])

# Среднее время загрузки (p50, p95, p99)
histogram_quantile(0.50, rate(bot_downloads_duration_seconds_bucket[5m]))
histogram_quantile(0.95, rate(bot_downloads_duration_seconds_bucket[5m]))
histogram_quantile(0.99, rate(bot_downloads_duration_seconds_bucket[5m]))
```

### Производительность

```promql
# Throughput (загрузок в секунду)
rate(bot_downloads_successful_total[1m])

# Процент ошибок
rate(bot_downloads_failed_total[5m]) / (rate(bot_downloads_successful_total[5m]) + rate(bot_downloads_failed_total[5m])) * 100

# Cache effectiveness
rate(bot_cache_hits_total[5m]) / (rate(bot_cache_hits_total[5m]) + rate(bot_cache_misses_total[5m])) * 100
```

### Ресурсы

```promql
# CPU usage
system_cpu_usage

# Memory usage (%)
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# GC time
rate(jvm_gc_pause_seconds_sum[1m])
```

## Best Practices

1. **Регулярный мониторинг:**
   - Проверяйте метрики ежедневно
   - Настройте алерты для критичных метрик
   - Используйте дашборды для визуализации

2. **Оптимизация:**
   - Если cache hit rate < 50%, проверьте TTL кэша
   - Если queue size растет, увеличьте `max-concurrent-downloads`
   - Если много parsing errors, проверьте парсер

3. **Capacity Planning:**
   - Отслеживайте trends в метриках
   - Планируйте масштабирование на основе метрик
   - Проводите load testing

4. **Troubleshooting:**
   - При росте errors проверьте логи
   - При медленных загрузках проверьте `bot.downloads.duration`
   - При проблемах с DB проверьте HikariCP метрики

## Экспорт метрик

### В Prometheus

Prometheus автоматически scrape'ит endpoint каждые 15 секунд (по умолчанию).

### Ручной экспорт

```bash
# Получить все метрики
curl http://localhost:8080/actuator/prometheus

# Отфильтровать конкретную метрику
curl http://localhost:8080/actuator/prometheus | grep bot_downloads
```

### В CloudWatch / Datadog

Для экспорта в облачные системы мониторинга:

1. Добавьте соответствующий registry в `build.gradle.kts`
2. Настройте credentials в `application.yml`
3. Метрики будут автоматически экспортироваться

## Дополнительные ресурсы

- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Docs](https://prometheus.io/docs/)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
