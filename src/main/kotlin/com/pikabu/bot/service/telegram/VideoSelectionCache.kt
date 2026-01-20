package com.pikabu.bot.service.telegram

import com.pikabu.bot.domain.model.VideoInfo
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
class VideoSelectionCache {

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val CACHE_TTL_MINUTES = 15L

    data class CacheEntry(
        val videos: List<VideoInfo>,
        val pageUrl: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun store(videos: List<VideoInfo>, pageUrl: String): String {
        cleanExpired()
        val id = UUID.randomUUID().toString().take(8)
        cache[id] = CacheEntry(videos, pageUrl)
        return id
    }

    fun get(id: String): CacheEntry? {
        val entry = cache[id]
        return if (entry != null && !isExpired(entry)) {
            entry
        } else {
            cache.remove(id)
            null
        }
    }

    private fun isExpired(entry: CacheEntry): Boolean {
        val age = System.currentTimeMillis() - entry.timestamp
        return age > TimeUnit.MINUTES.toMillis(CACHE_TTL_MINUTES)
    }

    private fun cleanExpired() {
        cache.entries.removeIf { isExpired(it.value) }
    }
}
