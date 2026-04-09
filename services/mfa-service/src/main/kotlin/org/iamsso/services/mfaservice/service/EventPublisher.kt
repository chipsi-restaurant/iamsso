package org.iamsso.services.mfaservice.service

import org.iamsso.contracts.events.CloudEventEnvelope
import org.iamsso.contracts.events.DomainEvent
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.contracts.events.MfaFactorEnrolledEvent
import org.iamsso.contracts.events.MfaFactorRemovedEvent
import org.iamsso.contracts.events.SendEmailOtpCommand
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class EventPublisher(private val kafka: KafkaTemplate<String, Any>) {

    fun mfaFactorEnrolled(userId: UUID, factorId: UUID, factorType: String) {
        send(KafkaTopics.MFA_EVENTS, userId, MfaFactorEnrolledEvent(userId, factorId, factorType))
    }

    fun mfaFactorRemoved(userId: UUID, factorId: UUID, factorType: String) {
        send(KafkaTopics.MFA_EVENTS, userId, MfaFactorRemovedEvent(userId, factorId, factorType))
    }

    fun sendEmailOtp(userId: UUID, email: String, code: String, expiresAt: Instant) {
        kafka.send(KafkaTopics.NOTIFICATION_COMMANDS, userId.toString(),
            SendEmailOtpCommand(userId, email, code, expiresAt))
    }

    private fun <T : DomainEvent> send(topic: String, userId: UUID, event: T) {
        kafka.send(topic, userId.toString(),
            CloudEventEnvelope(source = "mfa-service", type = event.eventType, data = event))
    }
}
