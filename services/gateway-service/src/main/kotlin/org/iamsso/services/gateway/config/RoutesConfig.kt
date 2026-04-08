package org.iamsso.services.gateway.config

import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RoutesConfig {

    @Bean
    fun routes(builder: RouteLocatorBuilder): RouteLocator = builder.routes()
        // Public — OAuth
        .route("auth-oauth2") { it.path("/oauth2/**").uri("http://localhost:8080") }
        .route("auth-wellknown") { it.path("/.well-known/**").uri("http://localhost:8080") }
        .route("auth-login") { it.path("/login").uri("http://localhost:8080") }
        .route("auth-userinfo") { it.path("/userinfo").uri("http://localhost:8080") }
        // Protected — user service
        .route("user-service") { it.path("/api/v1/users/**").uri("http://localhost:8081") }
        // Protected — auth admin
        .route("auth-clients") { it.path("/api/v1/clients/**").uri("http://localhost:8080") }
        .route("auth-sessions") { it.path("/api/v1/sessions/**").uri("http://localhost:8080") }
        // Protected — policy service
        .route("policy-policies") { it.path("/api/v1/policies/**").uri("http://localhost:8082") }
        .route("policy-roles") { it.path("/api/v1/roles/**").uri("http://localhost:8082") }
        .route("policy-evaluate") { it.path("/api/v1/policy/**").uri("http://localhost:8082") }
        .build()
}
