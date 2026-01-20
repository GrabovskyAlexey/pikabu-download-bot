# CI/CD Workflows

Этот проект использует GitHub Actions для автоматизации тестирования, сборки и релизов.

## Workflows

### 1. CI/CD Pipeline (`ci-cd.yml`)

**Триггеры:**
- Push в ветки: `master`, `main`, `develop`
- Pull requests в эти ветки

**Jobs:**

#### Test
- Запускает unit-тесты на JDK 17 и 21
- Генерирует отчеты о тестировании
- Загружает результаты как артефакты

#### Build
- Собирает JAR файл приложения
- Запускается только после успешного прохождения тестов
- Сохраняет JAR как артефакт на 30 дней

#### Lint
- Проверяет качество кода с помощью ktlint
- Выполняется параллельно с тестами

#### Docker
- Собирает Docker образ
- Push в GitHub Container Registry (ghcr.io)
- Запускается только для main/master ветки
- Использует BuildKit cache для ускорения
- Теги: `latest`, `{branch}-{sha}`

#### Deploy
- Автоматический deployment на production
- SSH подключение к серверу
- Pull нового образа и перезапуск
- Verification через health check

### 2. Release (`release.yml`)

**Триггеры:**
- Push тегов вида `v*` (например, `v1.0.0`)

**Процесс:**
1. Собирает приложение
2. Создает GitHub Release
3. Прикрепляет JAR файл к релизу

**Создание релиза:**
```bash
git tag v1.0.0
git push origin v1.0.0
```

### 3. Security Check (`security.yml`)

**Триггеры:**
- Расписание: каждый понедельник в 00:00 UTC
- Ручной запуск через GitHub UI

**Процесс:**
- Сканирует зависимости на известные уязвимости
- Генерирует отчет о безопасности
- Загружает отчет как артефакт

## Кэширование

Все workflows используют кэширование Gradle зависимостей для ускорения сборки:
- `~/.gradle/caches`
- `~/.gradle/wrapper`

## Артефакты

### Test Results
- Хранятся 7 дней
- Содержат результаты JUnit тестов

### Application JAR
- Хранится 30 дней
- Готов к deployment

### Dependency Check Report
- Хранится 30 дней
- HTML отчет об уязвимостях

## Локальная проверка перед push

Перед отправкой изменений рекомендуется локально проверить:

```bash
# Запуск тестов
./gradlew test

# Сборка
./gradlew build

# Проверка кода
./gradlew ktlintCheck
```

## Настройка для deployment

### GitHub Secrets

Добавьте следующие secrets в Settings → Secrets and variables → Actions:

| Secret | Описание | Пример |
|--------|----------|---------|
| `DEPLOY_HOST` | IP или hostname сервера | `123.45.67.89` или `bot.example.com` |
| `DEPLOY_USER` | SSH пользователь | `ubuntu` или `deploy` |
| `DEPLOY_SSH_KEY` | Приватный SSH ключ | Содержимое `~/.ssh/id_ed25519` |
| `DEPLOY_PORT` | SSH порт (опционально) | `22` (по умолчанию) |

### Генерация SSH ключа

```bash
# Генерируем новый ключ для GitHub Actions
ssh-keygen -t ed25519 -C "github-actions" -f ~/.ssh/github_actions_deploy

# Копируем публичный ключ на сервер
ssh-copy-id -i ~/.ssh/github_actions_deploy.pub user@server

# Содержимое приватного ключа добавляем в GitHub Secret
cat ~/.ssh/github_actions_deploy
```

### GitHub Container Registry

GHCR уже настроен и включен по умолчанию:

1. Repository Settings → Actions → General
2. Workflow permissions: **Read and write permissions** ✓

Образы публикуются по адресу:
```
ghcr.io/{owner}/{repo}:latest
ghcr.io/{owner}/{repo}:v1.0.0
```

### Deployment процесс

При push в `main` или `master`:
```
Test → Build → Docker Build → Deploy to Production
```

Deployment выполняет:
1. Login в GHCR
2. Pull нового Docker образа
3. `docker-compose down`
4. `docker-compose up -d`
5. Проверка статуса

## Мониторинг

Статус workflows можно отслеживать:
- GitHub Actions tab в репозитории
- Badges в README.md (можно добавить)
- Email уведомления (настраиваются в GitHub)

## Troubleshooting

### Tests failing
Проверьте логи test job, возможно:
- Изменилась логика и нужно обновить тесты
- Проблемы с зависимостями

### Build failing
- Проверьте, что все зависимости доступны
- Убедитесь что используется правильная версия JDK

### Cache issues
Если сборка очень медленная, можно очистить cache:
- Settings > Actions > Caches > Clear all caches
