package org.iamsso.services.authservice.service

import org.iamsso.contracts.events.CloudEventEnvelope
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.contracts.events.LoginFailedEvent
import org.iamsso.contracts.events.LoginSuccessEvent
import org.iamsso.contracts.events.SessionCreatedEvent
import org.iamsso.contracts.events.SessionDestroyedEvent
import org.iamsso.contracts.events.TokenIssuedEvent
import org.iamsso.contracts.events.TokenRevokedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthEventPublisher(
    private val kafka: KafkaTemplate<String, Any>,
) {
    fun publishLoginSuccess(userId: UUID, clientId: String, sessionId: String) =
        kafka.send(KafkaTopics.AUTH_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "auth.login-success",
                data = LoginSuccessEvent(userId = userId, clientId = clientId, sessionId = sessionId)))

    fun publishLoginFailed(identifier: String, clientId: String, reason: String) =
        kafka.send(KafkaTopics.AUTH_EVENTS, clientId,
            CloudEventEnvelope(source = "auth-service", type = "auth.login-failed",
                data = LoginFailedEvent(identifier = identifier, clientId = clientId, reason = reason)))

    fun publishTokenIssued(userId: UUID, clientId: String, grantType: String, scopes: List<String>, sessionId: String?) =
        kafka.send(KafkaTopics.AUTH_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "auth.token-issued",
                data = TokenIssuedEvent(userId = userId, clientId = clientId, grantType = grantType, scopes = scopes, sessionId = sessionId)))

    fun publishTokenRevoked(userId: UUID?, clientId: String, tokenType: String, reason: String) =
        kafka.send(KafkaTopics.AUTH_EVENTS, clientId,
            CloudEventEnvelope(source = "auth-service", type = "auth.token-revoked",
                data = TokenRevokedEvent(userId = userId, clientId = clientId, tokenType = tokenType, reason = reason)))

    fun publishSessionCreated(sessionId: String, userId: UUID, clientId: String) =
        kafka.send(KafkaTopics.SESSION_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "session.created",
                data = SessionCreatedEvent(sessionId = sessionId, userId = userId, clientId = clientId)))

    fun publishSessionDestroyed(sessionId: String, userId: UUID, reason: String) =
        kafka.send(KafkaTopics.SESSION_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "session.destroyed",
                data = SessionDestroyedEvent(sessionId = sessionId, userId = userId, reason = reason)))
}
