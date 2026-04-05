package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidScopeException
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
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
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientCredentialsGrantHandlerTest {

    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var authEventPublisher: AuthEventPublisher

    private lateinit var handler: ClientCredentialsGrantHandler

    private val client = OAuthClientEntity(
        clientId = "svc-client", clientName = "Service", clientSecretHash = "h",
        grantTypes = "client_credentials", redirectUris = "", scopes = "read,write",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 0,
    )

    @BeforeEach
    fun setUp() {
        handler = ClientCredentialsGrantHandler(jwtIssuer, authEventPublisher)
        whenever(jwtIssuer.issueAccessToken(anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn("svc-token")
    }

    @Test
    fun `handle issues access token without refresh token`() {
        val response = handler.handle(mapOf("scope" to "read"), client)
        assertNull(response.refreshToken)
        assertNull(response.idToken)
    }

    @Test
    fun `handle throws InvalidScopeException for unregistered scope`() {
        assertThrows<InvalidScopeException> { handler.handle(mapOf("scope" to "admin"), client) }
    }

    @Test
    fun `handle uses all client scopes when scope param absent`() {
        val response = handler.handle(emptyMap(), client)
        assertNull(response.refreshToken)
    }

    @Test
    fun `supports returns true only for client_credentials`() {
        assert(handler.supports("client_credentials"))
        assert(!handler.supports("authorization_code"))
    }
}
