package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RefreshTokenGrantHandlerTest {

    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var tokenFamilyService: TokenFamilyService
    @Mock lateinit var authEventPublisher: AuthEventPublisher

    private lateinit var handler: RefreshTokenGrantHandler
    private val props = AppProperties()

    private val clientId = "client-1"
    private val userId = UUID.randomUUID()
    private val familyId = UUID.randomUUID()
    private val rawToken = "my-refresh-token"
    private val tokenHash = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
    )

    private val client = OAuthClientEntity(
        clientId = clientId, clientName = "Test", clientSecretHash = "h",
        grantTypes = "refresh_token", redirectUris = "", scopes = "openid",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        handler = RefreshTokenGrantHandler(refreshTokenRepository, jwtIssuer, tokenFamilyService, authEventPublisher, props)
        whenever(jwtIssuer.issueAccessToken(anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn("new-access-token")
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }
    }

    private fun activeToken(revoked: Boolean = false) = RefreshTokenEntity(
        tokenHash = tokenHash,
        userId = userId,
        clientId = clientId,
        scopes = "openid",
        sessionId = null,
        expiresAt = Instant.now().plusSeconds(3600),
        revoked = revoked,
        familyId = familyId,
    )

    @Test
    fun `handle rotates refresh token and returns new access token`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(activeToken())
        val params = mapOf("refresh_token" to rawToken)
        val response = handler.handle(params, client)
        assertNotNull(response.accessToken)
        assertNotNull(response.refreshToken)
        verify(refreshTokenRepository, times(2)).save(any())
        verify(tokenFamilyService).addToFamily(any(), any())
    }

    @Test
    fun `handle detects reuse and revokes family`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(activeToken(revoked = true))
        val params = mapOf("refresh_token" to rawToken)
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
        verify(tokenFamilyService).revokeFamily(familyId)
    }

    @Test
    fun `handle throws when token not found`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(null)
        assertThrows<InvalidGrantException> { handler.handle(mapOf("refresh_token" to rawToken), client) }
    }

    @Test
    fun `handle throws when token expired`() {
        val expired = RefreshTokenEntity(
            tokenHash = tokenHash, userId = userId, clientId = clientId,
            scopes = "openid", sessionId = null,
            expiresAt = Instant.now().minusSeconds(10), familyId = familyId,
        )
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(expired)
        assertThrows<InvalidGrantException> { handler.handle(mapOf("refresh_token" to rawToken), client) }
        verify(tokenFamilyService, never()).revokeFamily(any())
    }

    @Test
    fun `supports returns true only for refresh_token`() {
        assert(handler.supports("refresh_token"))
        assert(!handler.supports("authorization_code"))
    }
}
