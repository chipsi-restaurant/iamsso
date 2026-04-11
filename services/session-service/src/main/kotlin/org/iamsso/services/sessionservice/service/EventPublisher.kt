package org.iamsso.services.sessionservice.service

import org.iamsso.contracts.events.CloudEventEnvelope
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.contracts.events.SessionCreatedEvent
import org.iamsso.contracts.events.SessionDestroyedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EventPublisher(private val kafka: KafkaTemplate<String, Any>) {

    fun sessionCreated(sessionId: String, userId: UUID, clientId: String) {
        kafka.send(
            KafkaTopics.SESSION_EVENTS,
            userId.toString(),
            CloudEventEnvelope(
                source = "session-service",
                type = "session.created",
                data = SessionCreatedEvent(sessionId = sessionId, userId = userId, clientId = clientId),
            ),
        )
    }

    fun sessionDestroyed(sessionId: String, userId: UUID, reason: String) {
        kafka.send(
            KafkaTopics.SESSION_EVENTS,
            userId.toString(),
            CloudEventEnvelope(
                source = "session-service",
                type = "session.destroyed",
                data = SessionDestroyedEvent(sessionId = sessionId, userId = userId, reason = reason),
            ),
        )
    }
}
