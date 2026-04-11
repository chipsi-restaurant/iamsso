package org.iamsso.services.userservice.service

import org.iamsso.contracts.events.CloudEventEnvelope
import org.iamsso.contracts.events.CredentialsPasswordChangedEvent
import org.iamsso.contracts.events.CredentialsPasswordResetEvent
import org.iamsso.contracts.events.CredentialsVerifyFailedEvent
import org.iamsso.contracts.events.DomainEvent
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.contracts.events.SendEmailVerificationCommand
import org.iamsso.contracts.events.SendPasswordResetCommand
import org.iamsso.contracts.events.UserCreatedEvent
import org.iamsso.contracts.events.UserDeletedEvent
import org.iamsso.contracts.events.UserProfileUpdatedEvent
import org.iamsso.contracts.events.UserStatusChangedEvent
import org.iamsso.contracts.events.UserUpdatedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class EventPublisher(private val kafka: KafkaTemplate<String, Any>) {

    fun userCreated(userId: UUID, email: String?, username: String?, status: String) {
        send(KafkaTopics.USER_EVENTS, userId, UserCreatedEvent(userId, email, username, status))
    }

    fun userUpdated(userId: UUID, changedFields: List<String>) {
        send(KafkaTopics.USER_EVENTS, userId, UserUpdatedEvent(userId, changedFields))
    }

    fun userDeleted(userId: UUID) {
        send(KafkaTopics.USER_EVENTS, userId, UserDeletedEvent(userId))
    }

    fun userStatusChanged(userId: UUID, oldStatus: String, newStatus: String, reason: String) {
        send(KafkaTopics.USER_EVENTS, userId, UserStatusChangedEvent(userId, oldStatus, newStatus, reason))
    }

    fun userProfileUpdated(userId: UUID, changedFields: List<String>) {
        send(KafkaTopics.USER_EVENTS, userId, UserProfileUpdatedEvent(userId, changedFields))
    }

    fun credentialsVerifyFailed(userId: UUID, failedAttempts: Int, ip: String?, userAgent: String?) {
        send(
            KafkaTopics.CREDENTIALS_EVENTS,
            userId,
            CredentialsVerifyFailedEvent(userId, failedAttempts, ip, userAgent)
        )
    }

    fun credentialsPasswordChanged(userId: UUID) {
        send(KafkaTopics.CREDENTIALS_EVENTS, userId, CredentialsPasswordChangedEvent(userId))
    }

    fun credentialsPasswordReset(userId: UUID, initiatedBy: String) {
        send(KafkaTopics.CREDENTIALS_EVENTS, userId, CredentialsPasswordResetEvent(userId, initiatedBy))
    }

    fun sendEmailVerification(userId: UUID, email: String, token: String, expiresAt: Instant) {
        kafka.send(
            KafkaTopics.NOTIFICATION_COMMANDS,
            userId.toString(),
            SendEmailVerificationCommand(userId, email, token, expiresAt)
        )
    }

    fun sendPasswordReset(userId: UUID, email: String, firstName: String?, token: String, expiresAt: Instant) {
        kafka.send(
            KafkaTopics.NOTIFICATION_COMMANDS,
            userId.toString(),
            SendPasswordResetCommand(userId, email, firstName, token, expiresAt)
        )
    }

    private fun <T : DomainEvent> send(topic: String, userId: UUID, event: T) {
        kafka.send(
            topic,
            userId.toString(),
            CloudEventEnvelope(source = "user-service", type = event.eventType, data = event)
        )
    }
}