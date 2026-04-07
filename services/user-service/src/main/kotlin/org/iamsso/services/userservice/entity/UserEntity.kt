package org.iamsso.services.userservice.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users", schema = "iamsso_users")
class UserEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(unique = true) var email: String? = null,
    @Column(unique = true) var username: String? = null,
    @Column(name = "display_name") var displayName: String? = null,
    @Column(name = "password_hash", nullable = false) var passwordHash: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: UserStatus = UserStatus.PENDING_VERIFICATION,
    @Column(name = "email_verified", nullable = false) var emailVerified: Boolean = false,
    @Column(nullable = false) var locale: String = "en",
    @Column(nullable = false) var role: String = "user",
    @Column(name = "failed_attempts", nullable = false) var failedAttempts: Int = 0,
    @Column(name = "locked_until") var lockedUntil: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY) var profile: UserProfileEntity? = null,
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true) val mfaFactors: MutableList<MfaFactorEntity> = mutableListOf(),
) {
    @PreUpdate
    fun onUpdate() { updatedAt = Instant.now() }
    val mfaEnabled: Boolean get() = mfaFactors.any { it.status == MfaFactorStatus.ACTIVE }
}

enum class UserStatus { PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED }