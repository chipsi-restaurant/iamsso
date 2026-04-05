package org.iamsso.services.authservice.service

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidClientException
import org.iamsso.services.authservice.exception.UnsupportedGrantTypeException
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64

@Service
class TokenService(
    private val clientRepository: OAuthClientRepository,
    private val passwordEncoder: PasswordEncoder,
    private val handlers: List<GrantHandler>,
) {
    fun issue(params: Map<String, String>, request: HttpServletRequest): TokenResponse {
        val client = authenticateClient(params, request)
        val grantType = params["grant_type"] ?: throw UnsupportedGrantTypeException("Missing grant_type")
        val handler = handlers.firstOrNull { it.supports(grantType) }
            ?: throw UnsupportedGrantTypeException("Unsupported grant type: $grantType")
        return handler.handle(params, client)
    }

    fun authenticateClient(params: Map<String, String>, request: HttpServletRequest): OAuthClientEntity {
        val (clientId, clientSecret) = extractCredentials(params, request)
        val client = clientRepository.findById(clientId).orElseThrow {
            InvalidClientException("Unknown client: $clientId")
        }
        if (!passwordEncoder.matches(clientSecret, client.clientSecretHash)) {
            if (client.previousSecretHash != null &&
                client.previousSecretExpiresAt != null &&
                client.previousSecretExpiresAt!!.isAfter(Instant.now()) &&
                passwordEncoder.matches(clientSecret, client.previousSecretHash)
            ) {
                return client
            }
            throw InvalidClientException("Invalid client credentials")
        }
        return client
    }

    private fun extractCredentials(params: Map<String, String>, request: HttpServletRequest): Pair<String, String> {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            val decoded = String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
            val colon = decoded.indexOf(':')
            if (colon > 0) return decoded.substring(0, colon) to decoded.substring(colon + 1)
        }
        val clientId = params["client_id"] ?: throw InvalidClientException("Missing client_id")
        val clientSecret = params["client_secret"] ?: throw InvalidClientException("Missing client_secret")
        return clientId to clientSecret
    }
}
