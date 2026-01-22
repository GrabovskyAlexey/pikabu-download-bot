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

    // Методы для админ-команд
    fun countByCreatedAtAfter(since: LocalDateTime): Long

    fun countByStatus(status: String): Long

    @Query("SELECT COUNT(DISTINCT e.userId) FROM DownloadHistoryEntity e")
    fun countDistinctUsers(): Long

    fun findTopByOrderByCreatedAtDesc(): DownloadHistoryEntity?

    @Query(
        """
        SELECT e.videoUrl, e.videoTitle, COUNT(e) as downloadCount
        FROM DownloadHistoryEntity e
        WHERE e.completedAt >= :since
        AND e.status = 'COMPLETED'
        GROUP BY e.videoUrl, e.videoTitle
        ORDER BY COUNT(e) DESC
        """
    )
    fun findTopVideosSince(since: LocalDateTime, pageable: org.springframework.data.domain.Pageable): List<Array<Any>>
}
