package org.iamsso.services.notificationservice.service

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.iamsso.services.notificationservice.config.AppProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.TemplateEngine
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailServiceTest {

    private val mailSender: JavaMailSender = mock()
    private val templateEngine: TemplateEngine = mock()
    private val props = AppProperties(
        mail = AppProperties.MailProps(from = "test@iamsso.local"),
        baseUrl = "http://localhost:8090",
    )

    private lateinit var emailService: EmailService
    private lateinit var capturedMessage: MimeMessage

    @BeforeEach
    fun setUp() {
        capturedMessage = MimeMessage(null as Session?)
        whenever(mailSender.createMimeMessage()).thenReturn(capturedMessage)
        whenever(templateEngine.process(any<String>(), any())).thenReturn("<html>dummy</html>")
        emailService = EmailService(mailSender, templateEngine, props)
    }

    @Test
    fun `sendEmailVerification sends email with verification URL`() {
        val userId = UUID.randomUUID()
        val email = "user@example.com"
        val token = "abc123"
        val expiresAt = Instant.now().plusSeconds(3600)

        emailService.sendEmailVerification(userId, email, token, expiresAt)

        verify(mailSender).send(any<MimeMessage>())
        assertEquals("user@example.com", capturedMessage.allRecipients[0].toString())
        assertTrue(capturedMessage.subject.contains("IAMSSO"))
    }

    @Test
    fun `sendMfaOtp sends email with OTP code`() {
        val userId = UUID.randomUUID()
        val email = "user@example.com"
        val code = "123456"
        val expiresAt = Instant.now().plusSeconds(300)

        emailService.sendMfaOtp(userId, email, code, expiresAt)

        verify(mailSender).send(any<MimeMessage>())
        assertEquals("user@example.com", capturedMessage.allRecipients[0].toString())
        assertTrue(capturedMessage.subject.contains("IAMSSO"))
    }

    @Test
    fun `sendPasswordChanged sends email`() {
        val userId = UUID.randomUUID()
        val email = "user@example.com"

        emailService.sendPasswordChanged(userId, email)

        verify(mailSender).send(any<MimeMessage>())
        assertEquals("user@example.com", capturedMessage.allRecipients[0].toString())
        assertTrue(capturedMessage.subject.contains("IAMSSO"))
    }

    @Test
    fun `sendSecurityAlert sends email with alert details`() {
        val userId = UUID.randomUUID()
        val email = "user@example.com"
        val alertType = "suspicious_login"
        val details = mapOf("ip" to "192.168.1.1", "location" to "Moscow")

        emailService.sendSecurityAlert(userId, email, alertType, details)

        verify(mailSender).send(any<MimeMessage>())
        assertEquals("user@example.com", capturedMessage.allRecipients[0].toString())
        assertTrue(capturedMessage.subject.contains("IAMSSO"))
    }
}
