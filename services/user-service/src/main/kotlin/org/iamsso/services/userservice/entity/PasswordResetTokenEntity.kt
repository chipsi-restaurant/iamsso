package org.iamsso.services.userservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "password_reset_tokens", schema = "iamsso_users")
class PasswordResetTokenEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) val userId: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true) val token: String = "",
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
)
