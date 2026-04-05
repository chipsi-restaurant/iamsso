package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.TokenResponse
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Component
class RefreshTokenGrantHandler(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer,
    private val tokenFamilyService: TokenFamilyService,
    private val authEventPublisher: AuthEventPublisher,
    private val props: AppProperties,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "refresh_token"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val rawToken = params["refresh_token"] ?: throw InvalidGrantException("Missing refresh_token")
        val hash = sha256(rawToken)
        val entity = refreshTokenRepository.findByTokenHash(hash) ?: throw InvalidGrantException("Invalid refresh token")

        if (entity.clientId != client.clientId) throw InvalidGrantException("Token issued to different client")

        if (entity.revoked) {
            entity.familyId?.let { tokenFamilyService.revokeFamily(it) }
            throw InvalidGrantException("Refresh token reuse detected")
        }

        if (entity.isExpired) throw InvalidGrantException("Refresh token expired")

        val newId = UUID.randomUUID()
        entity.revoked = true
        entity.replacedBy = newId
        refreshTokenRepository.save(entity)

        val scopes = entity.scopes.split(" ")
        val accessToken = jwtIssuer.issueAccessToken(
            userId = entity.userId,
            clientId = client.clientId,
            scopes = scopes,
            sessionId = entity.sessionId?.toString(),
            email = null,
            emailVerified = null,
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        val rawNewToken = UUID.randomUUID().toString()
        val newHash = sha256(rawNewToken)
        val familyId = entity.familyId ?: UUID.randomUUID()

        refreshTokenRepository.save(RefreshTokenEntity(
            id = newId,
            tokenHash = newHash,
            userId = entity.userId,
            clientId = client.clientId,
            scopes = entity.scopes,
            sessionId = entity.sessionId,
            expiresAt = Instant.now().plusSeconds(client.refreshTokenTtlSeconds.toLong()),
            familyId = familyId,
        ))
        tokenFamilyService.addToFamily(familyId, newHash)

        authEventPublisher.publishTokenIssued(
            userId = entity.userId,
            clientId = client.clientId,
            grantType = "refresh_token",
            scopes = scopes,
            sessionId = entity.sessionId?.toString(),
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            refreshToken = rawNewToken,
            scope = entity.scopes,
        )
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
