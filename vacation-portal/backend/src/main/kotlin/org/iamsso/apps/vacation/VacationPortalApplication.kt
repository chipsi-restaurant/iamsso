package org.iamsso.apps.vacation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VacationPortalApplication

fun main(args: Array<String>) {
    runApplication<VacationPortalApplication>(*args)
}
