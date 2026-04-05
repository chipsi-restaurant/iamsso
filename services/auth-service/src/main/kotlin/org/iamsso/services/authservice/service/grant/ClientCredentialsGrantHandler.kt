package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidScopeException
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenResponse
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ClientCredentialsGrantHandler(
    private val jwtIssuer: JwtIssuer,
    private val authEventPublisher: AuthEventPublisher,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "client_credentials"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val clientScopes = client.scopeList()
        val requestedScopes = params["scope"]?.split(" ")?.filter { it.isNotBlank() } ?: clientScopes
        if (requestedScopes.any { it !in clientScopes }) throw InvalidScopeException("Requested scope not registered")

        val accessToken = jwtIssuer.issueAccessToken(
            userId = null,
            clientId = client.clientId,
            scopes = requestedScopes,
            sessionId = null,
            email = null,
            emailVerified = null,
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        authEventPublisher.publishTokenIssued(
            userId = UUID.fromString("00000000-0000-0000-0000-000000000000"),
            clientId = client.clientId,
            grantType = "client_credentials",
            scopes = requestedScopes,
            sessionId = null,
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            scope = requestedScopes.joinToString(" "),
        )
    }
}
