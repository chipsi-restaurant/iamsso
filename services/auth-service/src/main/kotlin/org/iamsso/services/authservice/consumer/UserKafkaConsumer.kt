package org.iamsso.services.authservice.consumer

import org.iamsso.contracts.events.CloudEventEnvelope
import org.iamsso.contracts.events.DomainEvent
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.contracts.events.UserDeletedEvent
import org.iamsso.contracts.events.UserStatusChangedEvent
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.SessionServiceClient
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserKafkaConsumer(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionServiceClient: SessionServiceClient,
) {
    @KafkaListener(topics = [KafkaTopics.USER_EVENTS], groupId = "auth-service")
    @Transactional
    fun onUserEvent(envelope: CloudEventEnvelope<DomainEvent>) {
        when (val event = envelope.data) {
            is UserDeletedEvent -> {
                refreshTokenRepository.revokeAllByUserId(event.userId)
                sessionServiceClient.deleteAllForUser(event.userId)
            }
            is UserStatusChangedEvent -> {
                if (event.newStatus != "ACTIVE") {
                    refreshTokenRepository.revokeAllByUserId(event.userId)
                    sessionServiceClient.deleteAllForUser(event.userId)
                }
            }
            else -> Unit
        }
    }
}
