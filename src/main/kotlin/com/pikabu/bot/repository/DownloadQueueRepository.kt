package com.pikabu.bot.repository

import com.pikabu.bot.domain.model.QueueStatus
import com.pikabu.bot.entity.DownloadQueueEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DownloadQueueRepository : JpaRepository<DownloadQueueEntity, Long> {

    fun findByStatus(status: QueueStatus): List<DownloadQueueEntity>

    fun findByStatusOrderByCreatedAtAsc(status: QueueStatus): List<DownloadQueueEntity>

    fun findByUserId(userId: Long): List<DownloadQueueEntity>

    @Query("SELECT COUNT(e) FROM DownloadQueueEntity e WHERE e.status = :status")
    fun countByStatus(status: QueueStatus): Long

    @Query(
        "SELECT e FROM DownloadQueueEntity e " +
                "WHERE e.status = 'QUEUED' " +
                "ORDER BY e.createdAt ASC"
    )
    fun findNextPendingRequests(): List<DownloadQueueEntity>
}
