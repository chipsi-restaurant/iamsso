package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.TokenResponse
import org.iamsso.services.authservice.service.UserServiceClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Component
class AuthCodeGrantHandler(
    private val authCodeStore: AuthorizationCodeStore,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer,
    private val tokenFamilyService: TokenFamilyService,
    private val authEventPublisher: AuthEventPublisher,
    private val userServiceClient: UserServiceClient,
    private val props: AppProperties,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "authorization_code"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val code = params["code"] ?: throw InvalidGrantException("Missing code")
        val codeVerifier = params["code_verifier"] ?: throw InvalidGrantException("Missing code_verifier")
        val redirectUri = params["redirect_uri"] ?: throw InvalidGrantException("Missing redirect_uri")

        val codeData = authCodeStore.consume(code) ?: throw InvalidGrantException("Invalid or expired code")

        if (codeData.clientId != client.clientId) throw InvalidGrantException("Code issued to different client")
        if (codeData.redirectUri != redirectUri) throw InvalidGrantException("redirect_uri mismatch")
        if (!verifyPkce(codeVerifier, codeData.codeChallenge)) throw InvalidGrantException("PKCE verification failed")

        val scopes = codeData.scopes
        val accessToken = jwtIssuer.issueAccessToken(
            userId = codeData.userId,
            clientId = client.clientId,
            scopes = scopes,
            sessionId = codeData.sessionId,
            email = null,
            emailVerified = null,
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        val idToken = if ("openid" in scopes) {
            val profile = if ("profile" in scopes) userServiceClient.getProfile(codeData.userId) else null
            jwtIssuer.issueIdToken(
                userId = codeData.userId,
                clientId = client.clientId,
                nonce = codeData.nonce,
                scopes = scopes,
                ttlSeconds = props.jwt.idTokenTtlSeconds,
                displayName = profile?.displayName,
                preferredUsername = profile?.displayName,
                firstName = profile?.firstName,
                lastName = profile?.lastName,
                locale = profile?.locale,
            )
        } else null

        val rawRefreshToken = UUID.randomUUID().toString()
        val tokenHash = sha256(rawRefreshToken)
        val familyId = UUID.randomUUID()
        val sessionUuid = codeData.sessionId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        refreshTokenRepository.save(RefreshTokenEntity(
            tokenHash = tokenHash,
            userId = codeData.userId,
            clientId = client.clientId,
            scopes = scopes.joinToString(" "),
            sessionId = sessionUuid,
            expiresAt = Instant.now().plusSeconds(client.refreshTokenTtlSeconds.toLong()),
            familyId = familyId,
        ))
        tokenFamilyService.initFamily(familyId, tokenHash, Duration.ofSeconds(client.refreshTokenTtlSeconds.toLong()))

        authEventPublisher.publishTokenIssued(
            userId = codeData.userId,
            clientId = client.clientId,
            grantType = "authorization_code",
            scopes = scopes,
            sessionId = codeData.sessionId,
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            refreshToken = rawRefreshToken,
            idToken = idToken,
            scope = scopes.joinToString(" "),
        )
    }

    private fun verifyPkce(codeVerifier: String, codeChallenge: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest) == codeChallenge
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
