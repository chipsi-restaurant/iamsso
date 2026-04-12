package org.iamsso.services.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        // Strict CORS for API endpoints (frontend AJAX)
        val apiConfig = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:3000", "http://localhost:8090")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept")
            allowCredentials = true
        }

        // Permissive CORS for public server-rendered pages (login, password reset, MFA)
        // These are HTML form submissions — CORS не нужен для защиты,
        // безопасность обеспечивается backend-логикой (токены, anti-enumeration)
        val publicConfig = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "OPTIONS")
            allowCredentials = true
        }

        val source = UrlBasedCorsConfigurationSource()
        // Public form paths — permissive (specific first)
        source.registerCorsConfiguration("/login", publicConfig)
        source.registerCorsConfiguration("/forgot-password", publicConfig)
        source.registerCorsConfiguration("/reset-password", publicConfig)
        source.registerCorsConfiguration("/mfa-challenge", publicConfig)
        source.registerCorsConfiguration("/mfa-challenge/**", publicConfig)
        source.registerCorsConfiguration("/oauth2/**", publicConfig)
        // Everything else — strict
        source.registerCorsConfiguration("/**", apiConfig)
        return source
    }

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .authorizeExchange { it.anyExchange().permitAll() }
            .build()

    @Bean
    fun reactiveJwtDecoder(
        @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") jwkSetUri: String
    ): ReactiveJwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build()

    @Bean
    fun webClientBuilder(): WebClient.Builder = WebClient.builder()
}
