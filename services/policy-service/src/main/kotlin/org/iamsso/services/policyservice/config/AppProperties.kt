package org.iamsso.services.policyservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val evaluation: EvaluationProps = EvaluationProps(),
) {
    data class EvaluationProps(
        val defaultEffect: String = "DENY",
    )
}
