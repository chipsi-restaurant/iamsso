package org.iamsso.services.notificationservice.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.contracts.events.SendEmailOtpCommand
import org.iamsso.contracts.events.SendEmailVerificationCommand
import org.iamsso.contracts.events.SendPasswordChangedNotificationCommand
import org.iamsso.contracts.events.SendPasswordResetCommand
import org.iamsso.contracts.events.SendSecurityAlertCommand
import org.iamsso.services.notificationservice.service.EmailService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NotificationKafkaConsumer(
    private val emailService: EmailService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(NotificationKafkaConsumer::class.java)

    @KafkaListener(topics = [KafkaTopics.NOTIFICATION_COMMANDS], groupId = "notification-service")
    fun onNotificationCommand(message: String) {
        try {
            val node = objectMapper.readTree(message)

            // Determine if this is a CloudEventEnvelope or a direct command.
            // CloudEventEnvelope has "source", "type", and "data" fields.
            val isEnvelope = node.has("source") && node.has("type") && node.has("data")
            val envelopeType = if (isEnvelope) node.get("type").asText() else null
            val data = if (isEnvelope) node.get("data") else node

            when {
                envelopeType == "notification.send-password-reset" || matchesPasswordReset(data) -> {
                    val cmd = objectMapper.treeToValue(data, SendPasswordResetCommand::class.java)
                    emailService.sendPasswordReset(cmd.userId, cmd.email, cmd.firstName, cmd.token, cmd.expiresAt)
                }
                envelopeType == "notification.send-email-verification" || matchesVerification(data) -> {
                    val cmd = objectMapper.treeToValue(data, SendEmailVerificationCommand::class.java)
                    emailService.sendEmailVerification(cmd.userId, cmd.email, cmd.token, cmd.expiresAt)
                }
                envelopeType == "notification.send-email-otp" || matchesOtp(data) -> {
                    val cmd = objectMapper.treeToValue(data, SendEmailOtpCommand::class.java)
                    emailService.sendMfaOtp(cmd.userId, cmd.email, cmd.code, cmd.expiresAt)
                }
                envelopeType == "notification.password-changed" || matchesPasswordChanged(data) -> {
                    val cmd = objectMapper.treeToValue(data, SendPasswordChangedNotificationCommand::class.java)
                    emailService.sendPasswordChanged(cmd.userId, cmd.email)
                }
                envelopeType == "notification.security-alert" || matchesSecurityAlert(data) -> {
                    val cmd = objectMapper.treeToValue(data, SendSecurityAlertCommand::class.java)
                    emailService.sendSecurityAlert(cmd.userId, cmd.email, cmd.alertType, cmd.details)
                }
                else -> log.warn("Unknown notification command: {}", message)
            }
        } catch (e: Exception) {
            log.error("Failed to process notification command: {}", e.message, e)
        }
    }

    /** SendEmailVerificationCommand has "token" and "expiresAt" fields */
    private fun matchesVerification(node: JsonNode): Boolean =
        node.has("token") && node.has("expiresAt") && node.has("email")

    /**
     * SendPasswordResetCommand is distinguished by the "firstName" field, which is unique
     * to this command across all notification commands. Jackson (Spring Kafka JsonSerializer
     * uses default Include.ALWAYS) emits `"firstName": null` even when the value is null,
     * so `node.has("firstName")` reliably returns true for this command. Checked BEFORE
     * matchesVerification because both commands contain token + expiresAt + email.
     */
    private fun matchesPasswordReset(node: JsonNode): Boolean =
        node.has("firstName") && node.has("token") && node.has("expiresAt") && node.has("email")

    /** SendEmailOtpCommand has "code" and "expiresAt" fields */
    private fun matchesOtp(node: JsonNode): Boolean =
        node.has("code") && node.has("expiresAt") && node.has("email")

    /** SendSecurityAlertCommand has "alertType" */
    private fun matchesSecurityAlert(node: JsonNode): Boolean =
        node.has("alertType") && node.has("email")

    /** SendPasswordChangedNotificationCommand has only "userId" and "email" (no token/code/alertType) */
    private fun matchesPasswordChanged(node: JsonNode): Boolean =
        node.has("userId") && node.has("email") && !node.has("token") && !node.has("code") && !node.has("alertType")
}
