package org.iamsso.services.userservice.service

import org.iamsso.services.userservice.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

@Component
class SessionServiceClient(props: AppProperties) {
    private val log = LoggerFactory.getLogger(SessionServiceClient::class.java)
    private val client: RestClient = RestClient.create(props.sessionService.baseUrl)

    /**
     * Удалить все сессии пользователя. Возвращает true при успехе.
     * При сбое session-service логирует WARN и возвращает false — password reset
     * flow продолжается, так как пароль уже обновлён.
     */
    fun deleteAllForUser(userId: UUID): Boolean =
        try {
            client.delete().uri("/api/v1/sessions/user/{userId}", userId).retrieve().toBodilessEntity()
            true
        } catch (e: Exception) {
            log.warn("Failed to invalidate sessions for user={}: {}", userId, e.message)
            false
        }
}
