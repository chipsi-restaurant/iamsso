package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Топик: iam.credentials.events
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/** Неудачная попытка проверки пароля */
data class CredentialsVerifyFailedEvent(
    val userId: UUID,
    val failedAttempts: Int,
    val ip: String?,
    val userAgent: String?,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "credentials.verify-failed"
}

/** Пароль успешно изменён пользователем */
data class CredentialsPasswordChangedEvent(
    val userId: UUID,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "credentials.password-changed"
}

/** Пароль принудительно сброшен (администратором или через flow восстановления) */
data class CredentialsPasswordResetEvent(
    val userId: UUID,
    val initiatedBy: String,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "credentials.password-reset"
}
