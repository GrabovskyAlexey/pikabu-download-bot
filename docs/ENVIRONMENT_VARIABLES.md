# 🔐 Environment Variables Configuration

Как работают переменные окружения в Docker Compose.

## Автоматическая загрузка .env

Docker Compose **автоматически** загружает файл `.env` из директории с `docker-compose.yml`:

```
/opt/pikabu-bot/
├── docker-compose.yml
└── .env                 ← Автоматически загружается!
```

**Никакого явного указания не требуется** - это стандартное поведение Docker Compose.

## Как это работает

### 1. Создаете .env файл

```bash
cd /opt/pikabu-bot
cat > .env << EOF
BOT_TOKEN=123456:ABC-DEF
ADMIN_USER_ID=987654321
DB_PASSWORD=secret123
EOF
```

### 2. Используете в docker-compose.yml

```yaml
services:
  bot:
    environment:
      - BOT_TOKEN=${BOT_TOKEN}           # Значение из .env
      - ADMIN_USER_ID=${ADMIN_USER_ID}   # Значение из .env
      - SERVER_PORT=${SERVER_PORT:-8080} # Из .env или default 8080
```

### 3. Запускаете

```bash
docker compose up -d
```

Docker Compose автоматически:
1. Читает `.env`
2. Подставляет значения переменных
3. Передает их в контейнер

## Синтаксис переменных

### Простая подстановка

```yaml
${VARIABLE}              # Обязательная переменная (ошибка если нет)
```

### С default значением

```yaml
${VARIABLE:-default}     # Использовать default если не задано
${SERVER_PORT:-8080}     # Если SERVER_PORT нет, использовать 8080
```

### С пустым default

```yaml
${VARIABLE:-}            # Пустая строка если не задано
```

## Порядок приоритета

Docker Compose ищет переменные в таком порядке:

### 1. Environment в shell (высший приоритет)

```bash
export BOT_TOKEN=from_shell
docker compose up -d
# Использует: from_shell
```

### 2. Файл .env

```bash
# .env содержит:
BOT_TOKEN=from_env_file

docker compose up -d
# Использует: from_env_file (если не экспортирована в shell)
```

### 3. Default в docker-compose.yml

```yaml
environment:
  - BOT_TOKEN=${BOT_TOKEN:-default_value}

# Использует: default_value (если нет в shell и .env)
```

### Пример приоритета

```bash
# .env файл
SERVER_PORT=8080

# docker-compose.yml
environment:
  - SERVER_PORT=${SERVER_PORT:-3000}

# Запуск с переопределением
SERVER_PORT=9000 docker compose up -d

# Результат: 9000 (shell переменная выиграла)
```

## Проверка переменных

### Посмотреть подставленные значения

```bash
# Финальный конфиг с реальными значениями
docker compose config

# Только environment секция
docker compose config | grep -A 30 environment
```

### Проверить в контейнере

```bash
# Все переменные окружения
docker exec pikabu-bot env

# Конкретная переменная
docker exec pikabu-bot env | grep BOT_TOKEN

# Интерактивно
docker exec -it pikabu-bot sh
echo $BOT_TOKEN
echo $ADMIN_USER_ID
```

## Альтернативные способы

### Способ 1: env_file (другой файл)

Если нужно использовать файл с другим именем:

```yaml
services:
  bot:
    env_file:
      - .env.production
      - .env.secrets
```

Запуск:
```bash
docker compose up -d
```

### Способ 2: --env-file flag

```bash
# Явно указать файл
docker compose --env-file .env.production up -d

# Несколько файлов (последний приоритетнее)
docker compose --env-file .env --env-file .env.local up -d
```

### Способ 3: Shell экспорт

```bash
# Экспортируйте переменные
export BOT_TOKEN=123456
export ADMIN_USER_ID=789

# Запустите без .env файла
docker compose up -d
```

### Способ 4: Inline при запуске

```bash
# Одна переменная
BOT_TOKEN=123456 docker compose up -d

# Несколько переменных
BOT_TOKEN=123456 ADMIN_USER_ID=789 docker compose up -d
```

## Множественные .env файлы

Docker Compose поддерживает только **ОДИН** .env файл автоматически.

Для множественных используйте `env_file`:

```yaml
services:
  bot:
    env_file:
      - .env              # Базовые настройки
      - .env.local        # Локальные переопределения
      - .env.secrets      # Секреты (не в git!)
```

Порядок важен! Последний файл переопределяет предыдущие.

## Переменные в разных местах

### 1. Для docker-compose.yml (подстановка)

```yaml
ports:
  - "${SERVER_PORT}:8080"    # Подставляется из .env
image: "ghcr.io/${GITHUB_USER}/app:latest"
```

Эти переменные используются **при чтении** docker-compose.yml.

### 2. Для контейнера (environment)

```yaml
environment:
  - BOT_TOKEN=${BOT_TOKEN}
  - ADMIN_USER_ID=${ADMIN_USER_ID}
```

Эти переменные **передаются внутрь** контейнера.

### 3. Для сборки (build args)

```yaml
build:
  context: .
  args:
    - BUILD_VERSION=${VERSION}
```

Используются только **во время сборки** образа.

## Best Practices

### 1. Защита .env файла

```bash
# Установите права только для владельца
chmod 600 .env

# Проверьте
ls -la .env
# Должно быть: -rw------- 1 user user

# НЕ коммитьте в git!
echo ".env" >> .gitignore
```

### 2. Шаблон .env.example

Создайте пример без секретов:

```bash
# .env.example (в git)
BOT_TOKEN=your_token_here
ADMIN_USER_ID=your_id_here
DB_PASSWORD=change_this_password

# .env (не в git)
BOT_TOKEN=123456:ABC-DEF
ADMIN_USER_ID=987654321
DB_PASSWORD=super_secret_password
```

### 3. Проверка обязательных переменных

Используйте синтаксис без default для обязательных переменных:

```yaml
environment:
  - BOT_TOKEN=${BOT_TOKEN}              # Обязательно! Ошибка если нет
  - SERVER_PORT=${SERVER_PORT:-8080}    # Опционально, default 8080
```

### 4. Документируйте переменные

Создайте список в README или отдельном файле:

```markdown
## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| BOT_TOKEN | Yes | - | Telegram bot token from @BotFather |
| ADMIN_USER_ID | Yes | - | Your Telegram user ID |
| SERVER_PORT | No | 8080 | Application HTTP port |
```

## Troubleshooting

### Переменные не подставляются

**Проблема:** В контейнере пустые значения или literal `${VARIABLE}`.

**Решение:**

1. Проверьте что .env в той же директории:
   ```bash
   ls -la .env
   ```

2. Проверьте синтаксис .env (без пробелов вокруг `=`):
   ```bash
   # Правильно
   BOT_TOKEN=123456

   # Неправильно
   BOT_TOKEN = 123456
   BOT_TOKEN= 123456
   BOT_TOKEN =123456
   ```

3. Проверьте подстановку:
   ```bash
   docker compose config | grep BOT_TOKEN
   ```

### Переменные не видны в контейнере

**Проблема:** `docker exec app env` не показывает переменные.

**Решение:**

Убедитесь что переменные в секции `environment`:

```yaml
services:
  bot:
    environment:          # ← Должна быть эта секция
      - BOT_TOKEN=${BOT_TOKEN}
```

### Секреты в логах

**Проблема:** `docker compose config` показывает секреты.

**Решение:**

Используйте Docker secrets (для production):

```yaml
services:
  bot:
    secrets:
      - bot_token
    environment:
      - BOT_TOKEN=/run/secrets/bot_token

secrets:
  bot_token:
    file: ./secrets/bot_token.txt
```

### Кавычки в значениях

**Проблема:** Значение содержит спецсимволы.

**Решение:**

```bash
# В .env используйте кавычки для значений со спецсимволами
PASSWORD="p@ss!word#123"
MESSAGE='Hello "World"'

# Экранируйте $ если нужен literal
PATH_VAR="some/\$HOME/path"
```

## Примеры использования

### Development

```bash
# .env.dev
BOT_TOKEN=dev_token
ADMIN_USER_ID=123
DEBUG=true
LOG_LEVEL=DEBUG

docker compose --env-file .env.dev up
```

### Production

```bash
# .env.prod
BOT_TOKEN=prod_token
ADMIN_USER_ID=456
DEBUG=false
LOG_LEVEL=INFO

docker compose --env-file .env.prod up -d
```

### Testing

```bash
# Override для тестов
BOT_TOKEN=test_token \
ADMIN_USER_ID=999 \
docker compose -f docker-compose.test.yml up
```

## Ссылки

- [Docker Compose Environment Variables](https://docs.docker.com/compose/environment-variables/)
- [Compose File Reference - environment](https://docs.docker.com/compose/compose-file/05-services/#environment)
- [Compose File Reference - env_file](https://docs.docker.com/compose/compose-file/05-services/#env_file)

---

**TL;DR:** Docker Compose автоматически читает `.env` файл. Просто положите его рядом с `docker-compose.yml` и всё! 🎉
