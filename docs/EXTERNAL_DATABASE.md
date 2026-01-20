# 🗄️ Подключение к внешней PostgreSQL

Руководство по настройке бота для работы с уже существующей PostgreSQL базой данных.

> ⚠️ **Важно:** Проект использует схему `pikabu_bot`, а не `public`. См. [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md)

## Когда использовать

- ✅ PostgreSQL уже установлен на сервере
- ✅ Используете managed database (AWS RDS, DigitalOcean, etc.)
- ✅ Отдельный DB сервер
- ✅ Хотите использовать существующую PostgreSQL инсталляцию

## 🚀 Быстрый старт

### 1. Подготовка файлов

```bash
cd /opt/pikabu-bot

# Используйте версию для внешней БД
cp docker-compose.prod-external-db.yml docker-compose.yml
cp .env.production-external-db .env

# Отредактируйте конфигурацию
nano docker-compose.yml  # Замените yourusername
nano .env                # Настройте подключение к БД
```

### 2. Создание БД и пользователя

Подключитесь к PostgreSQL и выполните:

```sql
-- Создайте базу данных
CREATE DATABASE pikabu_bot;

-- Подключитесь к БД
\c pikabu_bot

-- Создайте пользователя
CREATE USER pikabu_user WITH PASSWORD 'your_secure_password';

-- Создайте схему pikabu_bot
CREATE SCHEMA IF NOT EXISTS pikabu_bot;

-- Дайте права на БД
GRANT ALL PRIVILEGES ON DATABASE pikabu_bot TO pikabu_user;

-- Дайте права на схему pikabu_bot
GRANT ALL ON SCHEMA pikabu_bot TO pikabu_user;

-- Дайте права на все таблицы в схеме (для будущих таблиц)
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON TABLES TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON SEQUENCES TO pikabu_user;
```

Или через bash:

```bash
# Как пользователь postgres
sudo -u postgres psql << 'EOF'
CREATE DATABASE pikabu_bot;
\c pikabu_bot
CREATE USER pikabu_user WITH PASSWORD 'your_secure_password';
CREATE SCHEMA IF NOT EXISTS pikabu_bot;
GRANT ALL PRIVILEGES ON DATABASE pikabu_bot TO pikabu_user;
GRANT ALL ON SCHEMA pikabu_bot TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON TABLES TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON SEQUENCES TO pikabu_user;
EOF
```

### 3. Настройка .env файла

Отредактируйте `/opt/pikabu-bot/.env`:

**Для PostgreSQL на том же сервере (localhost):**

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pikabu_bot?currentSchema=pikabu_bot
SPRING_DATASOURCE_USERNAME=pikabu_user
SPRING_DATASOURCE_PASSWORD=your_secure_password
```

**Для PostgreSQL на другом сервере:**

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://db-server.example.com:5432/pikabu_bot?currentSchema=pikabu_bot
SPRING_DATASOURCE_USERNAME=pikabu_user
SPRING_DATASOURCE_PASSWORD=your_secure_password
```

> 💡 **Важно:** Параметр `currentSchema=pikabu_bot` в URL гарантирует, что PostgreSQL будет использовать правильную схему, даже если что-то пойдет не так с настройками Hibernate.

### 4. Настройка docker-compose.yml

**Для localhost PostgreSQL:**

Раскомментируйте `network_mode: "host"` в `docker-compose.yml`:

```yaml
services:
  bot:
    image: ghcr.io/yourusername/pikabu-download-bot:latest
    container_name: pikabu-bot
    restart: unless-stopped

    # Включаем для подключения к localhost PostgreSQL
    network_mode: "host"

    # Закомментируйте ports, если используете network_mode: host
    # ports:
    #   - "${SERVER_PORT:-8080}:${SERVER_PORT:-8080}"
```

**Для внешнего PostgreSQL сервера:**

Оставьте как есть (без `network_mode: "host"`).

### 5. Запуск

```bash
cd /opt/pikabu-bot

# Проверка подключения к БД (опционально)
psql -h localhost -U pikabu_user -d pikabu_bot -c "SELECT 1"

# Запуск бота
docker compose up -d

# Проверка логов
docker compose logs -f bot
```

Миграции Liquibase применятся автоматически! ✨

## 🔧 Настройка PostgreSQL для внешних подключений

Если PostgreSQL на отдельном сервере, нужно разрешить внешние подключения.

### 1. Настройка postgresql.conf

```bash
sudo nano /etc/postgresql/16/main/postgresql.conf
```

Найдите и измените:

```conf
# Разрешить подключения со всех интерфейсов
listen_addresses = '*'

# Или только с определенного IP
# listen_addresses = '127.0.0.1,10.0.1.100'
```

### 2. Настройка pg_hba.conf

```bash
sudo nano /etc/postgresql/16/main/pg_hba.conf
```

Добавьте в конец файла:

```conf
# Разрешить подключение с Docker сети
# host  database    user         address          auth-method
host    pikabu_bot  pikabu_user  172.17.0.0/16    md5

# Или разрешить с определенного IP
# host  pikabu_bot  pikabu_user  10.0.1.100/32   md5

# Для localhost (если бот на том же сервере)
host    pikabu_bot  pikabu_user  127.0.0.1/32    md5
```

### 3. Перезапуск PostgreSQL

```bash
sudo systemctl restart postgresql
```

### 4. Проверка

```bash
# Проверка порта
sudo netstat -nltp | grep 5432

# Должно быть:
# tcp  0.0.0.0:5432  LISTEN

# Проверка подключения
psql -h localhost -U pikabu_user -d pikabu_bot
```

## 🔍 Определение Docker bridge IP

Если нужно узнать IP сеть Docker:

```bash
# Узнать Docker bridge сеть
docker network inspect bridge | grep Subnet

# Обычно: 172.17.0.0/16
```

## 🔒 Firewall настройки

Если используете firewall (ufw), откройте порт PostgreSQL:

```bash
# Для localhost - не нужно

# Для внешних подключений с определенного IP
sudo ufw allow from 10.0.1.100 to any port 5432

# Для Docker bridge сети
sudo ufw allow from 172.17.0.0/16 to any port 5432
```

## ✅ Проверка подключения

### Из контейнера

```bash
# Запустите временный контейнер для теста
docker run --rm -it postgres:16-alpine psql \
  -h host.docker.internal \
  -U pikabu_user \
  -d pikabu_bot
```

Если используете `network_mode: "host"`:

```bash
docker run --rm -it --network host postgres:16-alpine psql \
  -h localhost \
  -U pikabu_user \
  -d pikabu_bot
```

### Из хоста

```bash
psql -h localhost -U pikabu_user -d pikabu_bot -c "SELECT version()"
```

## 📊 Проверка миграций

После запуска бота проверьте, что миграции применились:

```bash
# Подключитесь к БД
psql -h localhost -U pikabu_user -d pikabu_bot

# Проверьте таблицы
\dt pikabu_bot.*

# Должны быть:
# pikabu_bot.databasechangelog
# pikabu_bot.databasechangeloglock
# pikabu_bot.download_history
# pikabu_bot.download_queue
# pikabu_bot.error_log
# pikabu_bot.rate_limit
# pikabu_bot.video_cache

# Выход
\q
```

## 🔄 Миграция данных

Если переносите с Docker PostgreSQL на внешний:

### 1. Backup из Docker

```bash
# Создайте backup
docker exec pikabu-postgres pg_dump -U pikabu_user pikabu_bot > backup.sql
```

### 2. Restore во внешнюю БД

```bash
# Создайте БД и пользователя (см. выше)

# Восстановите данные
psql -h localhost -U pikabu_user -d pikabu_bot < backup.sql
```

## 🆘 Troubleshooting

### "Connection refused"

**Проблема:** Бот не может подключиться к PostgreSQL.

**Решение:**

1. **Проверьте что PostgreSQL запущен:**
   ```bash
   sudo systemctl status postgresql
   ```

2. **Проверьте порт:**
   ```bash
   sudo netstat -nltp | grep 5432
   ```

3. **Проверьте pg_hba.conf:**
   ```bash
   sudo nano /etc/postgresql/16/main/pg_hba.conf
   # Добавьте правило для Docker IP
   ```

4. **Перезапустите PostgreSQL:**
   ```bash
   sudo systemctl restart postgresql
   ```

### "Password authentication failed"

**Проблема:** Неправильный пароль или пользователь.

**Решение:**

1. **Проверьте credentials в .env:**
   ```bash
   cat .env | grep DATASOURCE
   ```

2. **Проверьте пользователя в БД:**
   ```bash
   sudo -u postgres psql -c "\du"
   ```

3. **Сбросьте пароль:**
   ```bash
   sudo -u postgres psql
   ALTER USER pikabu_user WITH PASSWORD 'new_password';
   ```

### "Database does not exist"

**Проблема:** База данных не создана.

**Решение:**

```bash
sudo -u postgres psql
CREATE DATABASE pikabu_bot;
GRANT ALL PRIVILEGES ON DATABASE pikabu_bot TO pikabu_user;
```

### "Permission denied for schema pikabu_bot"

**Проблема:** У пользователя нет прав на схему.

**Решение:**

```bash
sudo -u postgres psql pikabu_bot << 'EOF'
CREATE SCHEMA IF NOT EXISTS pikabu_bot;
GRANT ALL ON SCHEMA pikabu_bot TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON TABLES TO pikabu_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA pikabu_bot GRANT ALL ON SEQUENCES TO pikabu_user;
EOF
```

### Docker не может подключиться к localhost

**Проблема:** `localhost` внутри контейнера - это сам контейнер.

**Решение:**

Используйте `network_mode: "host"` в docker-compose.yml:

```yaml
services:
  bot:
    network_mode: "host"
```

Или используйте `host.docker.internal` (только для Docker Desktop):

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/pikabu_bot
```

## 📋 Checklist

Перед запуском убедитесь:

- [ ] PostgreSQL установлен и запущен
- [ ] База данных `pikabu_bot` создана
- [ ] Пользователь `pikabu_user` создан с правами
- [ ] Схема `public` доступна пользователю (PostgreSQL 15+)
- [ ] `postgresql.conf` настроен (listen_addresses)
- [ ] `pg_hba.conf` разрешает подключения
- [ ] PostgreSQL перезапущен
- [ ] Firewall разрешает порт 5432 (если нужно)
- [ ] `.env` файл содержит правильный DATASOURCE_URL
- [ ] `docker-compose.yml` настроен (network_mode если нужно)
- [ ] Подключение тестируется успешно

## 🎯 Рекомендации

### Performance

Для production рекомендуется:

```sql
-- Увеличьте connection pool для высокой нагрузки
ALTER SYSTEM SET max_connections = 200;

-- Оптимизируйте память
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';

-- Автовакуум
ALTER SYSTEM SET autovacuum = on;
```

### Security

1. **Используйте сильные пароли**
2. **Ограничьте доступ по IP** в pg_hba.conf
3. **Используйте SSL** для внешних подключений:
   ```env
   SPRING_DATASOURCE_URL=jdbc:postgresql://db-server:5432/pikabu_bot?ssl=true&sslmode=require
   ```
4. **Регулярные backups:**
   ```bash
   # Добавьте в cron
   0 2 * * * pg_dump -U pikabu_user pikabu_bot | gzip > /backups/pikabu_bot_$(date +\%Y\%m\%d).sql.gz
   ```

### Monitoring

Используйте pg_stat для мониторинга:

```sql
-- Активные подключения
SELECT * FROM pg_stat_activity WHERE datname = 'pikabu_bot';

-- Размер БД
SELECT pg_size_pretty(pg_database_size('pikabu_bot'));

-- Статистика таблиц
SELECT * FROM pg_stat_user_tables WHERE schemaname = 'pikabu_bot';
```

## 📚 Дополнительные ресурсы

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Networking](https://docs.docker.com/network/)
- [Spring Boot Database](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html)

---

**Готово!** Теперь бот использует вашу существующую PostgreSQL базу данных! 🎉
