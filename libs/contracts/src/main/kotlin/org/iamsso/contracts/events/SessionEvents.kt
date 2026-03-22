package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

data class SessionCreatedEvent(
    override val eventType: String = "session.created",
    override val timestamp: Instant = Instant.now(),
    val sessionId: String,
    val userId: UUID,
    val clientId: String,
) : DomainEvent

data class SessionDestroyedEvent(
    override val eventType: String = "session.destroyed",
    override val timestamp: Instant = Instant.now(),
    val sessionId: String,
    val userId: UUID,
    val reason: String,
) : DomainEvent
