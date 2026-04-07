package org.iamsso.services.userservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mfa_factors", schema = "iamsso_users")
class MfaFactorEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) var user: UserEntity? = null,
    @Enumerated(EnumType.STRING) @Column(name = "factor_type", nullable = false) var factorType: MfaFactorType = MfaFactorType.TOTP,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: MfaFactorStatus = MfaFactorStatus.PENDING,
    @Column(name = "display_name") var displayName: String? = null,
    var secret: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
)

enum class MfaFactorType { TOTP, EMAIL_OTP }
enum class MfaFactorStatus { PENDING, ACTIVE }