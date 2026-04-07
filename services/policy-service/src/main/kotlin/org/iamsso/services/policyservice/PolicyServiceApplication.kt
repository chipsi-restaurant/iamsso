package org.iamsso.services.policyservice

import org.iamsso.services.policyservice.config.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(AppProperties::class)
class PolicyServiceApplication

fun main(args: Array<String>) {
    runApplication<PolicyServiceApplication>(*args)
}
