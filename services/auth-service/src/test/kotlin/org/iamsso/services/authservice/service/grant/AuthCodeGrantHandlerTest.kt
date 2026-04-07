package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.AuthorizationCodeData
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.UserServiceClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthCodeGrantHandlerTest {

    @Mock lateinit var authCodeStore: AuthorizationCodeStore
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var tokenFamilyService: TokenFamilyService
    @Mock lateinit var authEventPublisher: AuthEventPublisher
    @Mock lateinit var userServiceClient: UserServiceClient

    private lateinit var handler: AuthCodeGrantHandler
    private val props = AppProperties()

    private val clientId = "client-1"
    private val userId = UUID.randomUUID()
    private val redirectUri = "https://app.example.com/cb"
    private val codeVerifier = "abc123XYZverifier"
    private val codeChallenge: String = run {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private val client = OAuthClientEntity(
        clientId = clientId,
        clientName = "Test",
        clientSecretHash = "hash",
        grantTypes = "authorization_code",
        redirectUris = redirectUri,
        scopes = "openid email",
        accessTokenTtlSeconds = 3600,
        refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        handler = AuthCodeGrantHandler(
            authCodeStore, refreshTokenRepository, jwtIssuer,
            tokenFamilyService, authEventPublisher, userServiceClient, props,
        )
        whenever(jwtIssuer.issueAccessToken(anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn("access-token")
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }
    }

    private fun codeData(scopes: List<String> = listOf("openid")) = AuthorizationCodeData(
        code = "code-1",
        clientId = clientId,
        userId = userId,
        redirectUri = redirectUri,
        scopes = scopes,
        codeChallenge = codeChallenge,
        codeChallengeMethod = "S256",
        nonce = "nonce1",
        sessionId = UUID.randomUUID().toString(),
    )

    @Test
    fun `handle returns TokenResponse with access token`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData())
        val params = mapOf("code" to "code-1", "code_verifier" to codeVerifier, "redirect_uri" to redirectUri)
        val response = handler.handle(params, client)
        assertEquals("access-token", response.accessToken)
        assertNotNull(response.refreshToken)
        assertNull(response.idToken)
    }

    @Test
    fun `handle with openid scope returns id_token`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData(listOf("openid")))
        whenever(jwtIssuer.issueIdToken(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn("id-token")
        val params = mapOf("code" to "code-1", "code_verifier" to codeVerifier, "redirect_uri" to redirectUri)
        val response = handler.handle(params, client)
        assertEquals("id-token", response.idToken)
    }

    @Test
    fun `handle throws InvalidGrantException when code not found`() {
        whenever(authCodeStore.consume("bad-code")).thenReturn(null)
        val params = mapOf("code" to "bad-code", "code_verifier" to codeVerifier, "redirect_uri" to redirectUri)
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
    }

    @Test
    fun `handle throws InvalidGrantException when PKCE fails`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData())
        val params = mapOf("code" to "code-1", "code_verifier" to "wrong-verifier", "redirect_uri" to redirectUri)
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
    }

    @Test
    fun `handle throws InvalidGrantException when redirect_uri mismatch`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData())
        val params = mapOf("code" to "code-1", "code_verifier" to codeVerifier, "redirect_uri" to "https://evil.com/cb")
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
    }

    @Test
    fun `supports returns true only for authorization_code`() {
        assert(handler.supports("authorization_code"))
        assert(!handler.supports("refresh_token"))
    }
}
