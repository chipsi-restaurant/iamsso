package org.iamsso.services.notificationservice.service

import jakarta.mail.internet.MimeMessage
import org.iamsso.services.notificationservice.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.Instant
import java.util.UUID

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    fun sendEmailVerification(userId: UUID, email: String, token: String, expiresAt: Instant) {
        val ctx = Context().apply {
            setVariable("verificationUrl", "${props.baseUrl}/api/v1/users/$userId/verify-email?token=$token")
            setVariable("expiresAt", expiresAt.toString())
        }
        send(email, "Подтверждение email — IAMSSO", "email/email-verification", ctx)
    }

    fun sendMfaOtp(userId: UUID, email: String, code: String, expiresAt: Instant) {
        val ctx = Context().apply {
            setVariable("code", code)
            setVariable("expiresAt", expiresAt.toString())
        }
        send(email, "Код подтверждения — IAMSSO", "email/mfa-otp", ctx)
    }

    fun sendPasswordChanged(userId: UUID, email: String) {
        send(email, "Пароль изменён — IAMSSO", "email/password-changed", Context())
    }

    fun sendPasswordReset(userId: UUID, email: String, firstName: String?, token: String, expiresAt: Instant) {
        val ctx = Context().apply {
            setVariable("firstName", firstName)
            setVariable("resetUrl", "${props.baseUrl}/reset-password?token=$token")
            setVariable("expiresAt", expiresAt.toString())
        }
        send(email, "Сброс пароля — IAMSSO", "email/password-reset", ctx)
    }

    fun sendSecurityAlert(userId: UUID, email: String, alertType: String, details: Map<String, String>) {
        val ctx = Context().apply {
            setVariable("alertType", alertType)
            setVariable("details", details)
        }
        send(email, "Предупреждение безопасности — IAMSSO", "email/security-alert", ctx)
    }

    private fun send(to: String, subject: String, template: String, ctx: Context) {
        try {
            val html = templateEngine.process(template, ctx)
            val message: MimeMessage = mailSender.createMimeMessage()
            MimeMessageHelper(message, true, "UTF-8").apply {
                setFrom(props.mail.from)
                setTo(to)
                setSubject(subject)
                setText(html, true)
            }
            mailSender.send(message)
            log.info("Email sent to={} subject={}", to, subject)
        } catch (e: Exception) {
            log.error("Failed to send email to={} subject={}: {}", to, subject, e.message)
        }
    }
}
