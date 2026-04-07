package org.iamsso.contracts.events

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.UUID

/**
 * Обёртка CloudEvents для всех доменных событий IAM-платформы.
 *
 * Заголовки CloudEvents (ce_type, ce_source, ce_id, ce_time) передаются
 * как Kafka headers, а тело сообщения — сериализованный [data].
 */
data class CloudEventEnvelope<T : DomainEvent>(
    /** Уникальный идентификатор события */
    val id: UUID = UUID.randomUUID(),

    /** Источник события (имя сервиса) */
    val source: String,

    /** Тип события (например, user.created) */
    val type: String,

    /** Временная метка создания события */
    val time: Instant = Instant.now(),

    /** Полезная нагрузка */
    val data: T,
)

/**
 * Маркерный интерфейс для всех доменных событий.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "eventType",
)
@JsonSubTypes(
    // ── User ──
    JsonSubTypes.Type(value = UserCreatedEvent::class,        name = "user.created"),
    JsonSubTypes.Type(value = UserUpdatedEvent::class,        name = "user.updated"),
    JsonSubTypes.Type(value = UserDeletedEvent::class,        name = "user.deleted"),
    JsonSubTypes.Type(value = UserStatusChangedEvent::class,  name = "user.status-changed"),
    JsonSubTypes.Type(value = UserProfileUpdatedEvent::class, name = "user.profile-updated"),
    // ── Credentials ──
    JsonSubTypes.Type(value = CredentialsVerifyFailedEvent::class,  name = "credentials.verify-failed"),
    JsonSubTypes.Type(value = CredentialsPasswordChangedEvent::class, name = "credentials.password-changed"),
    JsonSubTypes.Type(value = CredentialsPasswordResetEvent::class,  name = "credentials.password-reset"),
    // ── MFA ──
    JsonSubTypes.Type(value = MfaFactorEnrolledEvent::class, name = "mfa.factor-enrolled"),
    JsonSubTypes.Type(value = MfaFactorRemovedEvent::class,  name = "mfa.factor-removed"),
    // ── Auth ──
    JsonSubTypes.Type(value = TokenIssuedEvent::class,     name = "auth.token-issued"),
    JsonSubTypes.Type(value = TokenRevokedEvent::class,    name = "auth.token-revoked"),
    JsonSubTypes.Type(value = LoginSuccessEvent::class,    name = "auth.login-success"),
    JsonSubTypes.Type(value = LoginFailedEvent::class,     name = "auth.login-failed"),
    // ── Session ──
    JsonSubTypes.Type(value = SessionCreatedEvent::class,  name = "session.created"),
    JsonSubTypes.Type(value = SessionDestroyedEvent::class, name = "session.destroyed"),
    // ── Policy ──
    JsonSubTypes.Type(value = PolicyCreatedEvent::class, name = "policy.created"),
    JsonSubTypes.Type(value = PolicyUpdatedEvent::class, name = "policy.updated"),
    JsonSubTypes.Type(value = PolicyDeletedEvent::class, name = "policy.deleted"),
)
sealed interface DomainEvent {
    val eventType: String
    val timestamp: Instant
}
