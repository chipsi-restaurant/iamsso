package org.iamsso.services.mfaservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val totp: TotpProps = TotpProps(),
    val emailOtp: EmailOtpProps = EmailOtpProps(),
) {
    data class TotpProps(val issuer: String = "IAMSSO")
    data class EmailOtpProps(val ttlSeconds: Long = 300)
}
