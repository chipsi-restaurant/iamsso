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
        .route("auth-forgot-password") { it.path("/forgot-password").uri("http://localhost:8080") }
        .route("auth-reset-password") { it.path("/reset-password").uri("http://localhost:8080") }
        .route("auth-userinfo") { it.path("/userinfo").uri("http://localhost:8080") }
        // Protected — user service
        .route("user-service") { it.path("/api/v1/users/**").uri("http://localhost:8081") }
        // Protected — auth admin
        .route("auth-clients") { it.path("/api/v1/clients/**").uri("http://localhost:8080") }
        .route("session-service") { it.path("/api/v1/sessions/**").uri("http://localhost:8086") }
        // Protected — policy service
        .route("policy-policies") { it.path("/api/v1/policies/**").uri("http://localhost:8082") }
        .route("policy-roles") { it.path("/api/v1/roles/**").uri("http://localhost:8082") }
        .route("policy-evaluate") { it.path("/api/v1/policy/**").uri("http://localhost:8082") }
        // Protected — MFA service
        .route("mfa-service") { it.path("/api/v1/mfa/**").uri("http://localhost:8083") }
        // Protected — audit service
        .route("audit-service") { it.path("/api/v1/audit/**").uri("http://localhost:8085") }
        // Health — proxied actuator endpoints
        .route("health-auth") { it.path("/health/auth").filters { f -> f.rewritePath("/health/auth", "/actuator/health") }.uri("http://localhost:8080") }
        .route("health-user") { it.path("/health/user").filters { f -> f.rewritePath("/health/user", "/actuator/health") }.uri("http://localhost:8081") }
        .route("health-policy") { it.path("/health/policy").filters { f -> f.rewritePath("/health/policy", "/actuator/health") }.uri("http://localhost:8082") }
        .route("health-mfa") { it.path("/health/mfa").filters { f -> f.rewritePath("/health/mfa", "/actuator/health") }.uri("http://localhost:8083") }
        .route("health-notification") { it.path("/health/notification").filters { f -> f.rewritePath("/health/notification", "/actuator/health") }.uri("http://localhost:8084") }
        .route("health-audit") { it.path("/health/audit").filters { f -> f.rewritePath("/health/audit", "/actuator/health") }.uri("http://localhost:8085") }
        .route("health-session") { it.path("/health/session").filters { f -> f.rewritePath("/health/session", "/actuator/health") }.uri("http://localhost:8086") }
        .build()
}
