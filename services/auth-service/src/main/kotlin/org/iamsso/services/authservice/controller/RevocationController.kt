package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.Base64

@RestController
class RevocationController(
    private val tokenService: TokenService,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @PostMapping("/oauth2/revoke", consumes = ["application/x-www-form-urlencoded"])
    fun revoke(
        @RequestParam params: Map<String, String>,
        request: HttpServletRequest,
    ) {
        val client = tokenService.authenticateClient(params, request)
        val token = params["token"] ?: return
        val hash = sha256(token)
        val entity = refreshTokenRepository.findByTokenHash(hash)
        if (entity != null && entity.clientId == client.clientId) {
            entity.revoked = true
            refreshTokenRepository.save(entity)
        }
        // RFC 7009: always 200, even if token not found
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
