package org.iamsso.contracts.events

/**
 * Константы названий Kafka-топиков IAM-платформы.
 *
 * Используются продюсерами и консьюмерами для единообразного
 * обращения к топикам без дублирования строковых литералов.
 */
object KafkaTopics {
    /** События жизненного цикла пользователей */
    const val USER_EVENTS = "iam.user.events"

    /** События, связанные с учётными данными (пароли) */
    const val CREDENTIALS_EVENTS = "iam.credentials.events"

    /** События MFA-факторов */
    const val MFA_EVENTS = "iam.mfa.events"

    /** Команды для Notification Service */
    const val NOTIFICATION_COMMANDS = "iam.notification.commands"

    /** События авторизации и выдачи токенов */
    const val AUTH_EVENTS = "iam.auth.events"

    /** События SSO-сессий */
    const val SESSION_EVENTS = "iam.session.events"
}
