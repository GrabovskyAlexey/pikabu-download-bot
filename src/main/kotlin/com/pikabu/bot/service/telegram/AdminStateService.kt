package com.pikabu.bot.service.telegram

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Сервис для хранения состояний диалогов с админом
 */
@Service
class AdminStateService {

    // Храним состояние для каждого админа (chatId -> AdminState)
    private val states = ConcurrentHashMap<Long, AdminState>()

    /**
     * Устанавливает состояние для админа
     */
    fun setState(chatId: Long, state: AdminState) {
        states[chatId] = state
        logger.debug { "Set state for admin $chatId: $state" }
    }

    /**
     * Получает текущее состояние админа
     */
    fun getState(chatId: Long): AdminState? {
        return states[chatId]
    }

    /**
     * Очищает состояние админа
     */
    fun clearState(chatId: Long) {
        states.remove(chatId)
        logger.debug { "Cleared state for admin $chatId" }
    }

    /**
     * Проверяет есть ли активное состояние у админа
     */
    fun hasState(chatId: Long): Boolean {
        return states.containsKey(chatId)
    }
}

/**
 * Возможные состояния диалога с админом
 */
enum class AdminState {
    /**
     * Ожидание ввода cookies для команды /update_auth
     */
    WAITING_FOR_COOKIES
}
