package org.iamsso.services.sessionservice

import org.iamsso.services.sessionservice.config.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(AppProperties::class)
class SessionServiceApplication

fun main(args: Array<String>) {
    runApplication<SessionServiceApplication>(*args)
}
