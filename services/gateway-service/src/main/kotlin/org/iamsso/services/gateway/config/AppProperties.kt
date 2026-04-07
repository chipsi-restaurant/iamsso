package org.iamsso.services.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val publicPaths: List<String> = emptyList(),
    val policyService: PolicyServiceProps = PolicyServiceProps(),
) {
    data class PolicyServiceProps(
        val evaluateUrl: String = "http://localhost:8082/api/v1/policy/evaluate",
    )
}
