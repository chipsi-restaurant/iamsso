package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Топик: iam.notification.commands
//
// Это команды, а не события — они предписывают
// Notification Service выполнить действие.
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/** Команда: отправить письмо для подтверждения email */
data class SendEmailVerificationCommand(
    val userId: UUID,
    val email: String,
    val token: String,
    val expiresAt: Instant,
    val timestamp: Instant = Instant.now(),
)

/** Команда: отправить OTP-код на email (MFA) */
data class SendEmailOtpCommand(
    val userId: UUID,
    val email: String,
    val code: String,
    val expiresAt: Instant,
    val timestamp: Instant = Instant.now(),
)

/** Команда: отправить уведомление о смене пароля */
data class SendPasswordChangedNotificationCommand(
    val userId: UUID,
    val email: String,
    val timestamp: Instant = Instant.now(),
)

/** Команда: отправить письмо со ссылкой для сброса пароля */
data class SendPasswordResetCommand(
    val userId: UUID,
    val email: String,
    val firstName: String?,
    val token: String,
    val expiresAt: Instant,
    val timestamp: Instant = Instant.now(),
)

/** Команда: отправить предупреждение о подозрительной активности */
data class SendSecurityAlertCommand(
    val userId: UUID,
    val email: String,
    val alertType: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Instant = Instant.now(),
)
