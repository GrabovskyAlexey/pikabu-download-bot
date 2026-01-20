package com.pikabu.bot.repository

import com.pikabu.bot.entity.RateLimitEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RateLimitRepository : JpaRepository<RateLimitEntity, Long> {

    fun findByUserId(userId: Long): Optional<RateLimitEntity>

    fun deleteByUserId(userId: Long)
}
