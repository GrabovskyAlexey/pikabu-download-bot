package com.pikabu.bot.repository

import com.pikabu.bot.entity.ErrorLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ErrorLogRepository : JpaRepository<ErrorLogEntity, Long> {

    fun findByErrorType(errorType: String): List<ErrorLogEntity>

    @Query(
        "SELECT e FROM ErrorLogEntity e " +
                "WHERE e.errorType = :errorType " +
                "AND e.occurredAt >= :since " +
                "ORDER BY e.occurredAt DESC"
    )
    fun findRecentErrorsByType(errorType: String, since: LocalDateTime): List<ErrorLogEntity>

    @Query(
        "SELECT e FROM ErrorLogEntity e " +
                "WHERE e.occurredAt >= :since " +
                "ORDER BY e.occurredAt DESC"
    )
    fun findRecentErrors(since: LocalDateTime): List<ErrorLogEntity>

    @Query(
        "SELECT e FROM ErrorLogEntity e " +
                "WHERE e.notifiedAdmin = false " +
                "AND e.occurredAt >= :since"
    )
    fun findUnnotifiedErrors(since: LocalDateTime): List<ErrorLogEntity>

    @Query("SELECT COUNT(e) FROM ErrorLogEntity e WHERE e.errorType = :errorType AND e.occurredAt >= :since")
    fun countByErrorTypeSince(errorType: String, since: LocalDateTime): Long
}
