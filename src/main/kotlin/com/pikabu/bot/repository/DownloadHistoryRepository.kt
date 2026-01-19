package com.pikabu.bot.repository

import com.pikabu.bot.entity.DownloadHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface DownloadHistoryRepository : JpaRepository<DownloadHistoryEntity, Long> {

    fun findByUserId(userId: Long): List<DownloadHistoryEntity>

    fun findByUserIdOrderByCompletedAtDesc(userId: Long): List<DownloadHistoryEntity>

    @Query("SELECT COUNT(e) FROM DownloadHistoryEntity e WHERE e.completedAt >= :since")
    fun countCompletedSince(since: LocalDateTime): Long

    @Query(
        "SELECT e FROM DownloadHistoryEntity e " +
                "WHERE e.completedAt >= :since " +
                "ORDER BY e.completedAt DESC"
    )
    fun findCompletedSince(since: LocalDateTime): List<DownloadHistoryEntity>
}
