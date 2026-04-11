package org.iamsso.services.authservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(private val jwtKeyProvider: JwtKeyProvider) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // Chain 1: Public OAuth/OIDC endpoints — /oauth2/*, /.well-known/*, /userinfo, /actuator/*
    @Bean
    @Order(1)
    fun oauthFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/oauth2/**", "/.well-known/**", "/userinfo", "/actuator/**", "/login", "/mfa-challenge", "/mfa-challenge/**", "/forgot-password", "/reset-password", "/api/v1/sessions/logout")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    // Chain 2: API endpoints — /api/v1/* — permitAll (authorization handled by API Gateway + Policy Service)
    @Bean
    @Order(2)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/api/v1/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    @Bean
    fun localJwtDecoder(): NimbusJwtDecoder =
        NimbusJwtDecoder.withPublicKey(jwtKeyProvider.publicKey).build()
}
