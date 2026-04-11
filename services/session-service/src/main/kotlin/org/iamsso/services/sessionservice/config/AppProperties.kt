package org.iamsso.services.sessionservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val ssoSession: SsoSessionProps = SsoSessionProps(),
) {
    data class SsoSessionProps(val ttlSeconds: Long = 86400)
}
