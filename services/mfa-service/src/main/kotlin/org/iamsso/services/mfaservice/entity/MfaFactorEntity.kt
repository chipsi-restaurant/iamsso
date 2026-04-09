package org.iamsso.services.mfaservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mfa_factors", schema = "iamsso_mfa")
class MfaFactorEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) var userId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) @Column(name = "factor_type", nullable = false) var factorType: MfaFactorType = MfaFactorType.TOTP,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: MfaFactorStatus = MfaFactorStatus.PENDING,
    @Column(name = "display_name") var displayName: String? = null,
    var secret: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
)

enum class MfaFactorType { TOTP, EMAIL_OTP }
enum class MfaFactorStatus { PENDING, ACTIVE }
