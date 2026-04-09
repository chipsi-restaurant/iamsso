package org.iamsso.services.notificationservice.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.iamsso.services.notificationservice.service.EmailService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Instant
import java.util.UUID

class NotificationKafkaConsumerTest {

    private val emailService: EmailService = mock()
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    private lateinit var consumer: NotificationKafkaConsumer

    @BeforeEach
    fun setUp() {
        consumer = NotificationKafkaConsumer(emailService, objectMapper)
    }

    @Test
    fun `dispatches SendEmailVerificationCommand`() {
        val userId = UUID.randomUUID()
        val expiresAt = Instant.now().plusSeconds(3600)
        val json = """
            {
                "userId": "$userId",
                "email": "user@example.com",
                "token": "verify-token",
                "expiresAt": "$expiresAt"
            }
        """.trimIndent()

        consumer.onNotificationCommand(json)

        verify(emailService).sendEmailVerification(
            eq(userId),
            eq("user@example.com"),
            eq("verify-token"),
            eq(expiresAt),
        )
    }

    @Test
    fun `dispatches SendEmailOtpCommand`() {
        val userId = UUID.randomUUID()
        val expiresAt = Instant.now().plusSeconds(300)
        val json = """
            {
                "userId": "$userId",
                "email": "user@example.com",
                "code": "123456",
                "expiresAt": "$expiresAt"
            }
        """.trimIndent()

        consumer.onNotificationCommand(json)

        verify(emailService).sendMfaOtp(
            eq(userId),
            eq("user@example.com"),
            eq("123456"),
            eq(expiresAt),
        )
    }

    @Test
    fun `dispatches SendPasswordChangedNotificationCommand`() {
        val userId = UUID.randomUUID()
        val json = """
            {
                "userId": "$userId",
                "email": "user@example.com"
            }
        """.trimIndent()

        consumer.onNotificationCommand(json)

        verify(emailService).sendPasswordChanged(
            eq(userId),
            eq("user@example.com"),
        )
    }

    @Test
    fun `handles unknown type without error`() {
        val json = """
            {
                "source": "test",
                "type": "unknown.command",
                "data": { "foo": "bar" }
            }
        """.trimIndent()

        consumer.onNotificationCommand(json)

        verifyNoInteractions(emailService)
    }
}
