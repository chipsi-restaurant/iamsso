package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

data class TokenIssuedEvent(
    override val eventType: String = "auth.token-issued",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID,
    val clientId: String,
    val grantType: String,
    val scopes: List<String>,
    val sessionId: String?,
) : DomainEvent

data class TokenRevokedEvent(
    override val eventType: String = "auth.token-revoked",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID?,
    val clientId: String,
    val tokenType: String,
    val reason: String,
) : DomainEvent

data class LoginSuccessEvent(
    override val eventType: String = "auth.login-success",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID,
    val clientId: String,
    val sessionId: String,
    val mfaUsed: Boolean = false,
    val ipAddress: String? = null,
) : DomainEvent

data class LoginFailedEvent(
    override val eventType: String = "auth.login-failed",
    override val timestamp: Instant = Instant.now(),
    val identifier: String,
    val clientId: String,
    val reason: String,
    val ipAddress: String? = null,
) : DomainEvent
