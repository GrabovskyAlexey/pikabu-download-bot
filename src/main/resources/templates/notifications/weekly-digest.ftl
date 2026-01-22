📊 Недельная статистика (за 7 дней)

✅ Загружено видео: ${stats.successfulDownloads}
📈 Среднее в день: ${stats.avgDownloadsPerDay?string["0.0"]}

❌ Всего ошибок: ${stats.totalErrors}
   • Парсинг: ${stats.parsingErrors}
   • Загрузка: ${stats.downloadErrors}
   • Система: ${stats.systemErrors}

👥 Активных за неделю: ${stats.activeUsers}
🌐 Всего пользователей: ${stats.totalUsers}
📦 В очереди сейчас: ${stats.queuedRequests}
<#if stats.topVideos?has_content>

🔥 Топ популярных видео:
<#list stats.topVideos as video>
${video?index + 1}. ${(video.videoTitle!?length > 60)?then(video.videoTitle[0..59] + "...", video.videoTitle!"Без названия")}
   📥 Скачиваний: ${video.downloadCount}
   🔗 ${video.videoUrl}
<#if video?has_next>

</#if>
</#list>
</#if>
