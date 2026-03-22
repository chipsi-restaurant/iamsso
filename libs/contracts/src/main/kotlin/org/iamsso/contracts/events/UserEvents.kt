package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Топик: iam.user.events
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/** Пользователь зарегистрирован */
data class UserCreatedEvent(
    val userId: UUID,
    val email: String?,
    val username: String?,
    val status: String,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "user.created"
}

/** Данные пользователя обновлены */
data class UserUpdatedEvent(
    val userId: UUID,
    val changedFields: List<String>,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "user.updated"
}

/** Пользователь мягко удалён */
data class UserDeletedEvent(
    val userId: UUID,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "user.deleted"
}

/** Статус учётной записи изменён */
data class UserStatusChangedEvent(
    val userId: UUID,
    val oldStatus: String,
    val newStatus: String,
    val reason: String,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "user.status-changed"
}

/** Профиль пользователя обновлён */
data class UserProfileUpdatedEvent(
    val userId: UUID,
    val changedFields: List<String>,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "user.profile-updated"
}
