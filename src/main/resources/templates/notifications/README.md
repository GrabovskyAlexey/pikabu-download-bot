# Шаблоны уведомлений FreeMarker

Эта папка содержит шаблоны для всех типов уведомлений администратору.

## Доступные шаблоны

### Ошибки

- **parsing-errors.ftl** - уведомления об ошибках парсинга
  - Переменные: `errorCount`, `lastError` (ErrorLogEntity)

- **download-errors.ftl** - уведомления об ошибках загрузки
  - Переменные: `errorCount`, `lastError` (ErrorLogEntity)

- **system-error.ftl** - критические системные ошибки
  - Переменные: `error` (ErrorLogEntity)

### Авторизация

- **authentication-error.ftl** - ошибки авторизации (401, 403)
  - Переменные: `statusCode` (Int), `url` (String)

- **cookies-expired.ftl** - протухшие cookies
  - Переменные: `url` (String)

### Дайджесты

- **daily-digest.ftl** - дневная статистика
  - Переменные: `stats` (DailyStats)
    - successfulDownloads: Int
    - totalErrors: Int
    - parsingErrors: Int
    - downloadErrors: Int
    - systemErrors: Int
    - activeUsers: Int
    - queuedRequests: Int

- **weekly-digest.ftl** - недельная статистика с топом видео
  - Переменные: `stats` (WeeklyStats)
    - successfulDownloads: Int
    - totalErrors: Int
    - parsingErrors: Int
    - downloadErrors: Int
    - systemErrors: Int
    - activeUsers: Int
    - totalUsers: Int
    - queuedRequests: Int
    - avgDownloadsPerDay: Double
    - topVideos: List<PopularVideo>
      - videoUrl: String
      - videoTitle: String?
      - downloadCount: Long

## Синтаксис FreeMarker

### Основные директивы

```ftl
${variable}                    - Вывод переменной
${variable!"Значение по умолчанию"}  - С дефолтом
${variable?string("0.0")}      - Форматирование числа
${variable?length}             - Длина строки
${variable[0..59]}            - Substring
${variable?string("yyyy-MM-dd HH:mm:ss")}  - Форматирование даты
```

### Условия

```ftl
<#if condition>
  ...
<#elseif otherCondition>
  ...
<#else>
  ...
</#if>
```

### Проверка на null

```ftl
<#if variable??>
  Variable exists
</#if>

<#if list?has_content>
  List is not empty
</#if>
```

### Циклы

```ftl
<#list items as item>
  ${item?index + 1}. ${item.name}
  <#if item?has_next>
    separator
  </#if>
</#list>
```

### Тернарный оператор

```ftl
${(condition)?then("true value", "false value")}
```

## Примеры редактирования

### Изменить формат даты

```ftl
🕐 Время: ${error.occurredAt?string("dd.MM.yyyy в HH:mm")}
```

### Добавить эмодзи

```ftl
🔥 Топ популярных видео:
⭐ ${video.videoTitle}
```

### Изменить форматирование числа

```ftl
📈 Среднее в день: ${stats.avgDownloadsPerDay?string["0.00"]}
```

## Документация FreeMarker

Полная документация: https://freemarker.apache.org/docs/

После изменения шаблонов необходим перезапуск бота для применения изменений.
