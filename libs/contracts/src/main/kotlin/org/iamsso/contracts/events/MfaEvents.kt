package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Топик: iam.mfa.events
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/** Новый MFA-фактор подтверждён и активирован */
data class MfaFactorEnrolledEvent(
    val userId: UUID,
    val factorId: UUID,
    val factorType: String,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "mfa.factor-enrolled"
}

/** MFA-фактор удалён */
data class MfaFactorRemovedEvent(
    val userId: UUID,
    val factorId: UUID,
    val factorType: String,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "mfa.factor-removed"
}
