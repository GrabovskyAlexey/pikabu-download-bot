package com.pikabu.bot.repository

import com.pikabu.bot.entity.VideoCacheEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface VideoCacheRepository : JpaRepository<VideoCacheEntity, String> {

    @Modifying
    @Query("UPDATE VideoCacheEntity v SET v.lastUsedAt = :lastUsedAt WHERE v.videoUrl = :videoUrl")
    fun updateLastUsedAt(videoUrl: String, lastUsedAt: LocalDateTime)

    @Modifying
    @Query("DELETE FROM VideoCacheEntity v WHERE v.lastUsedAt < :cutoffDate")
    fun deleteOlderThan(cutoffDate: LocalDateTime): Int
}
