package org.iamsso.services.userservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val credentials: CredentialsProps = CredentialsProps(),
    val emailVerification: EmailVerificationProps = EmailVerificationProps(),
    val sessionService: SessionServiceProps = SessionServiceProps(),
) {
    data class CredentialsProps(
        val maxFailedAttempts: Int = 5,
        val lockDurationMinutes: Long = 30,
    )
    data class EmailVerificationProps(
        val tokenTtlHours: Long = 24,
        val resendCooldownSeconds: Long = 60,
    )
    data class SessionServiceProps(
        val baseUrl: String = "http://localhost:8086",
    )
}