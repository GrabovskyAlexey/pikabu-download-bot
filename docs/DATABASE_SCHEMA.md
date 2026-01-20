# 🗄️ Database Schema Configuration

Проект использует **отдельную схему `pikabu_bot`**, а НЕ стандартную `public`.

## Почему отдельная схема?

- ✅ **Изоляция:** Не засоряем схему `public`
- ✅ **Безопасность:** Разделение прав доступа
- ✅ **Организация:** Все таблицы в одном namespace
- ✅ **Best Practice:** Рекомендуется для production приложений

## Конфигурация

Схема настроена в **трёх** местах для максимальной надежности:

### 1. JDBC URL (самый надежный)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pikabu_bot?currentSchema=pikabu_bot
```

Параметр `currentSchema=pikabu_bot` в URL - это **прямая инструкция PostgreSQL** использовать схему `pikabu_bot`. Это самый надежный способ!

### 2. Hibernate настройки

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_schema: pikabu_bot  # Hibernate будет использовать эту схему
```

### 3. Liquibase настройки

```yaml
spring:
  liquibase:
    default-schema: pikabu_bot      # Liquibase создаст таблицы в этой схеме
```

> 💡 **Best Practice:** Использовать все три способа вместе. Это обеспечивает максимальную защиту от случайного использования `public` схемы.

### 2. Liquibase migrations

Все миграции создают таблицы в схеме `pikabu_bot` автоматически.

### 3. Создание схемы

При настройке БД **обязательно создайте схему**:

```sql
CREATE DATABASE pikabu_bot;
\c pikabu_bot

-- Создайте схему
CREATE SCHEMA IF NOT EXISTS pikabu_bot;

-- Создайте пользователя
CREATE USER pikabu_user WITH PASSWORD 'password';

-- Дайте права на схему
GRANT ALL ON SCHEMA pikabu_bot TO pikabu_user;

-- Права на будущие таблицы
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot
  GRANT ALL ON TABLES TO pikabu_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot
  GRANT ALL ON SEQUENCES TO pikabu_user;
```

## Таблицы в схеме pikabu_bot

После первого запуска в схеме будут созданы:

```
pikabu_bot.databasechangelog          # Liquibase служебная
pikabu_bot.databasechangeloglock      # Liquibase служебная
pikabu_bot.download_history           # История загрузок
pikabu_bot.download_queue             # Очередь загрузок
pikabu_bot.error_log                  # Логи ошибок
pikabu_bot.rate_limit                 # Rate limiting
pikabu_bot.video_cache                # Кэш file_id
```

## Проверка схемы

### Посмотреть все таблицы в схеме:

```sql
\c pikabu_bot
\dt pikabu_bot.*
```

### Посмотреть текущую схему:

```sql
SELECT current_schema();
SHOW search_path;
```

### Список всех схем:

```sql
\dn
```

## Troubleshooting

### "Schema pikabu_bot does not exist"

**Проблема:** Схема не создана.

**Решение:**
```sql
\c pikabu_bot
CREATE SCHEMA IF NOT EXISTS pikabu_bot;
```

### "Permission denied for schema pikabu_bot"

**Проблема:** У пользователя нет прав.

**Решение:**
```sql
GRANT ALL ON SCHEMA pikabu_bot TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot
  GRANT ALL ON TABLES TO pikabu_user;
```

### Таблицы создались в public вместо pikabu_bot

**Проблема:** Неправильно настроена конфигурация.

**Решение:**
1. Проверьте `application.yml`:
   ```yaml
   hibernate.default_schema: pikabu_bot
   liquibase.default-schema: pikabu_bot
   ```

2. Пересоздайте таблицы:
   ```sql
   -- Удалите из public
   DROP SCHEMA public CASCADE;
   CREATE SCHEMA public;

   -- Создайте в pikabu_bot
   CREATE SCHEMA IF NOT EXISTS pikabu_bot;

   -- Перезапустите приложение
   ```

### Миграция из public в pikabu_bot

Если нужно перенести данные из `public` в `pikabu_bot`:

```sql
-- 1. Создайте новую схему
CREATE SCHEMA IF NOT EXISTS pikabu_bot;

-- 2. Перенесите таблицы
ALTER TABLE public.download_history SET SCHEMA pikabu_bot;
ALTER TABLE public.download_queue SET SCHEMA pikabu_bot;
ALTER TABLE public.error_log SET SCHEMA pikabu_bot;
ALTER TABLE public.rate_limit SET SCHEMA pikabu_bot;
ALTER TABLE public.video_cache SET SCHEMA pikabu_bot;
ALTER TABLE public.databasechangelog SET SCHEMA pikabu_bot;
ALTER TABLE public.databasechangeloglock SET SCHEMA pikabu_bot;

-- 3. Дайте права
GRANT ALL ON SCHEMA pikabu_bot TO pikabu_user;
GRANT ALL ON ALL TABLES IN SCHEMA pikabu_bot TO pikabu_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA pikabu_bot TO pikabu_user;
```

## Почему currentSchema в URL?

### Тройная защита

Мы используем **три уровня** настройки схемы:

1. **JDBC URL** - `?currentSchema=pikabu_bot`
   - Самый приоритетный способ
   - Прямая инструкция PostgreSQL driver
   - Работает всегда, независимо от других настроек

2. **Hibernate** - `default_schema: pikabu_bot`
   - Используется для JPA операций
   - Может не применяться к нативным запросам

3. **Liquibase** - `default-schema: pikabu_bot`
   - Только для миграций
   - Не влияет на runtime запросы

### Что может пойти не так без currentSchema?

Без параметра в URL:
- Native SQL queries могут пойти в `public`
- @Query с нативным SQL может искать в `public`
- Прямые JDBC запросы пойдут в `public`
- Сторонние библиотеки могут игнорировать Hibernate настройки

С параметром в URL:
- ✅ PostgreSQL **всегда** ищет в `pikabu_bot` первым
- ✅ Работает для всех типов запросов
- ✅ Защита от ошибок конфигурации

### Пример проблемы

```kotlin
// Без currentSchema в URL:
@Query(value = "SELECT * FROM download_queue", nativeQuery = true)
fun findAll(): List<DownloadQueue>
// Ищет в: public.download_queue ❌

// С currentSchema в URL:
@Query(value = "SELECT * FROM download_queue", nativeQuery = true)
fun findAll(): List<DownloadQueue>
// Ищет в: pikabu_bot.download_queue ✅
```

## Best Practices

1. **ВСЕГДА указывайте currentSchema в JDBC URL** - это самый надежный способ
2. **Всегда создавайте схему явно** при инициализации БД
3. **Дайте права на схему** пользователю приложения
4. **Используйте ALTER DEFAULT PRIVILEGES** для автоматических прав на новые таблицы
5. **Не используйте схему public** для приложений
6. **Проверяйте схему после миграций** с помощью `\dt pikabu_bot.*`

## Связанные файлы

- `src/main/resources/application.yml` - конфигурация Spring
- `src/main/resources/db/changelog/` - Liquibase миграции
- `docs/EXTERNAL_DATABASE.md` - настройка внешней БД
- `PRODUCTION_CHECKLIST.md` - deployment инструкции

---

**Важно:** Все скрипты и инструкции в проекте уже настроены для использования схемы `pikabu_bot`. Просто следуйте им! 🎉
