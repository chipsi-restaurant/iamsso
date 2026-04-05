package org.iamsso.services.authservice.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens", schema = "iamsso_auth")
class RefreshTokenEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String = "",

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Column(name = "client_id", nullable = false)
    val clientId: String = "",

    @Column(nullable = false)
    val scopes: String = "",

    @Column(name = "session_id")
    val sessionId: UUID? = null,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(name = "replaced_by")
    var replacedBy: UUID? = null,

    @Column(name = "family_id")
    var familyId: UUID? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    val isExpired: Boolean get() = expiresAt.isBefore(Instant.now())
    val isActive: Boolean get() = !revoked && !isExpired
}
