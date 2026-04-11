package org.iamsso.services.auditservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val consumerGroup: String = "audit-service",
)
