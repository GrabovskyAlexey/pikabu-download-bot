package com.pikabu.bot.repository

import com.pikabu.bot.entity.AuthConfigEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AuthConfigRepository : JpaRepository<AuthConfigEntity, Long> {

    /**
     * Поиск конфигурации по ключу
     */
    fun findByConfigKey(configKey: String): Optional<AuthConfigEntity>

    /**
     * Удаление конфигурации по ключу
     */
    fun deleteByConfigKey(configKey: String)
}
