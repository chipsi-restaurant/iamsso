package org.iamsso.services.mfaservice

import org.iamsso.services.mfaservice.config.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(AppProperties::class)
class MfaServiceApplication

fun main(args: Array<String>) {
    runApplication<MfaServiceApplication>(*args)
}
