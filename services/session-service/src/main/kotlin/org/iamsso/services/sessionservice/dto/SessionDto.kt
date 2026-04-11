package org.iamsso.services.sessionservice.dto

import java.time.Instant
import java.util.UUID

data class CreateSessionRequest(
    val userId: UUID,
    val clientId: String,
)

data class SessionResponse(
    val sessionId: String,
    val userId: UUID,
    val clientIds: List<String>,
    val createdAt: Instant,
    val lastActivityAt: Instant,
    val expiresAt: Instant,
)

data class AddClientRequest(
    val clientId: String,
)
