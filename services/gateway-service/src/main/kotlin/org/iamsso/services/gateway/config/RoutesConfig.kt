package org.iamsso.services.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RoutesConfig(
    @Value("\${app.services.auth-url}") private val authUrl: String,
    @Value("\${app.services.user-url}") private val userUrl: String,
    @Value("\${app.services.policy-url}") private val policyUrl: String,
    @Value("\${app.services.mfa-url}") private val mfaUrl: String,
    @Value("\${app.services.notification-url}") private val notificationUrl: String,
    @Value("\${app.services.audit-url}") private val auditUrl: String,
    @Value("\${app.services.session-url}") private val sessionUrl: String,
    @Value("\${app.services.vacation-url}") private val vacationUrl: String,
) {

    @Bean
    fun routes(builder: RouteLocatorBuilder): RouteLocator = builder.routes()
        // Public — OAuth
        .route("auth-oauth2") { it.path("/oauth2/**").uri(authUrl) }
        .route("auth-wellknown") { it.path("/.well-known/**").uri(authUrl) }
        .route("auth-login") { it.path("/login").uri(authUrl) }
        .route("auth-forgot-password") { it.path("/forgot-password").uri(authUrl) }
        .route("auth-reset-password") { it.path("/reset-password").uri(authUrl) }
        .route("auth-mfa-challenge") { it.path("/mfa-challenge", "/mfa-challenge/**").uri(authUrl) }
        .route("auth-userinfo") { it.path("/userinfo").uri(authUrl) }
        // Protected — user service
        .route("user-service") { it.path("/api/v1/users/**").uri(userUrl) }
        // Protected — auth admin
        .route("auth-clients") { it.path("/api/v1/clients/**").uri(authUrl) }
        // Specific logout — must come BEFORE session-service wildcard (first-match)
        .route("auth-logout") { it.path("/api/v1/sessions/logout").uri(authUrl) }
        .route("auth-logout-all") { it.path("/api/v1/sessions/logout-all").uri(authUrl) }
        .route("session-service") { it.path("/api/v1/sessions/**").uri(sessionUrl) }
        // Protected — policy service
        .route("policy-policies") { it.path("/api/v1/policies/**").uri(policyUrl) }
        .route("policy-roles") { it.path("/api/v1/roles/**").uri(policyUrl) }
        .route("policy-evaluate") { it.path("/api/v1/policy/**").uri(policyUrl) }
        // Protected — MFA service
        .route("mfa-service") { it.path("/api/v1/mfa/**").uri(mfaUrl) }
        // Protected — audit service
        .route("audit-service") { it.path("/api/v1/audit/**").uri(auditUrl) }
        // Protected — vacation portal backend
        .route("vacation-portal") { it.path("/api/v1/vacation-requests/**").uri(vacationUrl) }
        // Health — proxied actuator endpoints
        .route("health-auth") { it.path("/health/auth").filters { f -> f.rewritePath("/health/auth", "/actuator/health") }.uri(authUrl) }
        .route("health-user") { it.path("/health/user").filters { f -> f.rewritePath("/health/user", "/actuator/health") }.uri(userUrl) }
        .route("health-policy") { it.path("/health/policy").filters { f -> f.rewritePath("/health/policy", "/actuator/health") }.uri(policyUrl) }
        .route("health-mfa") { it.path("/health/mfa").filters { f -> f.rewritePath("/health/mfa", "/actuator/health") }.uri(mfaUrl) }
        .route("health-notification") { it.path("/health/notification").filters { f -> f.rewritePath("/health/notification", "/actuator/health") }.uri(notificationUrl) }
        .route("health-audit") { it.path("/health/audit").filters { f -> f.rewritePath("/health/audit", "/actuator/health") }.uri(auditUrl) }
        .route("health-session") { it.path("/health/session").filters { f -> f.rewritePath("/health/session", "/actuator/health") }.uri(sessionUrl) }
        .build()
}
