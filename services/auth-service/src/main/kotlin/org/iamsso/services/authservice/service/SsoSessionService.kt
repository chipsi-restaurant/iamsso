package org.iamsso.services.authservice.service

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

interface SsoSessionService {
    fun create(userId: UUID, firstClientId: String): SsoSession
    fun get(sessionId: String): SsoSession?
    fun addClient(sessionId: String, clientId: String)
    fun updateActivity(sessionId: String)
    fun delete(sessionId: String)
    fun deleteAllForUser(userId: UUID)
}
