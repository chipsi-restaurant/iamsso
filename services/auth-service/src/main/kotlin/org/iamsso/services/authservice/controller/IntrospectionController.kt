package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.Base64

@RestController
class IntrospectionController(
    private val tokenService: TokenService,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @PostMapping("/oauth2/introspect", consumes = ["application/x-www-form-urlencoded"])
    fun introspect(
        @RequestParam params: Map<String, String>,
        request: HttpServletRequest,
    ): Map<String, Any?> {
        tokenService.authenticateClient(params, request)
        val token = params["token"] ?: return mapOf("active" to false)

        val claims = jwtIssuer.verify(token)
        if (claims != null) {
            return mapOf(
                "active" to true,
                "sub" to claims.subject,
                "scope" to claims.getStringClaim("scope"),
                "client_id" to claims.audience?.firstOrNull(),
                "exp" to claims.expirationTime.time / 1000,
                "iss" to claims.issuer,
            )
        }

        val hash = sha256(token)
        val entity = refreshTokenRepository.findByTokenHash(hash)
        return if (entity != null && entity.isActive) {
            mapOf(
                "active" to true,
                "sub" to entity.userId.toString(),
                "scope" to entity.scopes,
                "client_id" to entity.clientId,
                "exp" to entity.expiresAt.epochSecond,
            )
        } else {
            mapOf("active" to false)
        }
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
