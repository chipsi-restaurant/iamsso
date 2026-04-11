package org.iamsso.services.authservice.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class SessionEventConsumer(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(SessionEventConsumer::class.java)

    @KafkaListener(
        topics = [KafkaTopics.SESSION_EVENTS],
        groupId = "auth-service-session-consumer",
        properties = [
            "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        ],
    )
    @Transactional
    fun onSessionEvent(message: String) {
        try {
            val node = objectMapper.readTree(message)
            val type = node.get("type")?.asText() ?: return
            if (type != "session.destroyed") return

            val data = node.get("data") ?: return
            val sessionIdStr = data.get("sessionId")?.asText() ?: return
            val sessionUuid = runCatching { UUID.fromString(sessionIdStr) }.getOrNull() ?: return

            val revoked = refreshTokenRepository.revokeAllBySessionId(sessionUuid)
            log.info("Revoked {} refresh tokens for destroyed session {}", revoked, sessionIdStr)
        } catch (e: Exception) {
            log.error("Failed to process session event: {}", e.message, e)
        }
    }
}
