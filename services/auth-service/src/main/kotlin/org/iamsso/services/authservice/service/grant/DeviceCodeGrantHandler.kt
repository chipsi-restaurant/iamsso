package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.AccessDeniedException
import org.iamsso.services.authservice.exception.AuthorizationPendingException
import org.iamsso.services.authservice.exception.ExpiredTokenException
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.exception.SlowDownException
import org.iamsso.services.authservice.repository.DeviceCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.TokenResponse
import org.iamsso.services.authservice.service.UserServiceClient
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Component
class DeviceCodeGrantHandler(
    private val deviceCodeStore: DeviceCodeStore,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer,
    private val tokenFamilyService: TokenFamilyService,
    private val authEventPublisher: AuthEventPublisher,
    private val userServiceClient: UserServiceClient,
    private val redis: StringRedisTemplate,
    private val props: AppProperties,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "urn:ietf:params:oauth:grant-type:device_code"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val deviceCode = params["device_code"] ?: throw InvalidGrantException("Missing device_code")
        val data = deviceCodeStore.findByDeviceCode(deviceCode)
            ?: throw ExpiredTokenException("Device code expired or not found")

        if (data.denied) throw AccessDeniedException("User denied the device authorization")

        if (!data.approved) {
            val pollKey = "auth:device:poll:$deviceCode"
            val notRapid = redis.opsForValue().setIfAbsent(
                pollKey, "1", Duration.ofSeconds(props.deviceCode.pollingIntervalSeconds.toLong())
            ) ?: true
            if (!notRapid) throw SlowDownException()
            throw AuthorizationPendingException()
        }

        val userId = data.userId ?: throw InvalidGrantException("Approved but userId missing")
        val scopes = data.scopes
        val userData = userServiceClient.getById(userId)
        val accessToken = jwtIssuer.issueAccessToken(
            userId = userId,
            clientId = client.clientId,
            scopes = scopes,
            sessionId = null,
            email = null,
            emailVerified = null,
            role = userData?.role,
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        val rawRefreshToken = UUID.randomUUID().toString()
        val tokenHash = sha256(rawRefreshToken)
        val familyId = UUID.randomUUID()

        refreshTokenRepository.save(RefreshTokenEntity(
            tokenHash = tokenHash,
            userId = userId,
            clientId = client.clientId,
            scopes = scopes.joinToString(" "),
            sessionId = null,
            expiresAt = Instant.now().plusSeconds(client.refreshTokenTtlSeconds.toLong()),
            familyId = familyId,
        ))
        tokenFamilyService.initFamily(familyId, tokenHash, Duration.ofSeconds(client.refreshTokenTtlSeconds.toLong()))

        deviceCodeStore.delete(data)

        authEventPublisher.publishTokenIssued(
            userId = userId,
            clientId = client.clientId,
            grantType = "urn:ietf:params:oauth:grant-type:device_code",
            scopes = scopes,
            sessionId = null,
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            refreshToken = rawRefreshToken,
            scope = scopes.joinToString(" "),
        )
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
