package org.iamsso.services.notificationservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val mail: MailProps = MailProps(),
    val baseUrl: String = "http://localhost:8090",
) {
    data class MailProps(
        val from: String = "noreply@iamsso.local",
    )
}
