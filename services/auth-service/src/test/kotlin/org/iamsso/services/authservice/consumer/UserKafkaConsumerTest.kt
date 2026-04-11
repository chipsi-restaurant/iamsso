package org.iamsso.services.authservice.consumer

import org.iamsso.contracts.events.CloudEventEnvelope
import org.iamsso.contracts.events.DomainEvent
import org.iamsso.contracts.events.UserDeletedEvent
import org.iamsso.contracts.events.UserStatusChangedEvent
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.SessionServiceClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Instant
import java.util.UUID

class UserKafkaConsumerTest {

    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var sessionServiceClient: SessionServiceClient
    private lateinit var consumer: UserKafkaConsumer

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        consumer = UserKafkaConsumer(refreshTokenRepository, sessionServiceClient)
    }

    @Test
    fun `user deleted event revokes tokens and sessions`() {
        val userId = UUID.randomUUID()
        val event = CloudEventEnvelope(
            source = "user-service",
            type = "user.deleted",
            data = UserDeletedEvent(userId = userId, timestamp = Instant.now()),
        )
        @Suppress("UNCHECKED_CAST")
        consumer.onUserEvent(event as CloudEventEnvelope<DomainEvent>)
        verify(refreshTokenRepository).revokeAllByUserId(userId)
        verify(sessionServiceClient).deleteAllForUser(userId)
    }

    @Test
    fun `user status changed to ACTIVE does nothing`() {
        val userId = UUID.randomUUID()
        val event = CloudEventEnvelope(
            source = "user-service",
            type = "user.status-changed",
            data = UserStatusChangedEvent(
                userId = userId,
                oldStatus = "SUSPENDED",
                newStatus = "ACTIVE",
                reason = "admin",
                timestamp = Instant.now(),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        consumer.onUserEvent(event as CloudEventEnvelope<DomainEvent>)
        verifyNoInteractions(refreshTokenRepository)
        verifyNoInteractions(sessionServiceClient)
    }

    @Test
    fun `user status changed to SUSPENDED revokes tokens`() {
        val userId = UUID.randomUUID()
        val event = CloudEventEnvelope(
            source = "user-service",
            type = "user.status-changed",
            data = UserStatusChangedEvent(
                userId = userId,
                oldStatus = "ACTIVE",
                newStatus = "SUSPENDED",
                reason = "admin",
                timestamp = Instant.now(),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        consumer.onUserEvent(event as CloudEventEnvelope<DomainEvent>)
        verify(refreshTokenRepository).revokeAllByUserId(userId)
        verify(sessionServiceClient).deleteAllForUser(userId)
    }
}
