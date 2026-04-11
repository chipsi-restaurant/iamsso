package org.iamsso.services.sessionservice.model

import java.time.Instant
import java.util.UUID

data class SsoSession(
    val sessionId: String,
    val userId: UUID,
    val clientIds: MutableList<String>,
    val createdAt: Instant,
    val lastActivityAt: Instant,
    val expiresAt: Instant,
)
