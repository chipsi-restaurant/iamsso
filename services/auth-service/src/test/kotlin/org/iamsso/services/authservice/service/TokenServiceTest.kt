package org.iamsso.services.authservice.service

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidClientException
import org.iamsso.services.authservice.exception.UnsupportedGrantTypeException
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Base64
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class TokenServiceTest {

    @Mock lateinit var clientRepository: OAuthClientRepository
    @Mock lateinit var request: HttpServletRequest

    private val passwordEncoder = BCryptPasswordEncoder()
    private val secret = "my-secret"
    private lateinit var service: TokenService

    private val client = OAuthClientEntity(
        clientId = "client-1",
        clientName = "Test",
        clientSecretHash = BCryptPasswordEncoder().encode("my-secret")!!,
        grantTypes = "authorization_code",
        redirectUris = "",
        scopes = "openid",
        accessTokenTtlSeconds = 3600,
        refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        service = TokenService(clientRepository, passwordEncoder, emptyList())
        whenever(request.getHeader("Authorization")).thenReturn(null)
    }

    @Test
    fun `authenticateClient succeeds with form params`() {
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val params = mapOf("client_id" to "client-1", "client_secret" to secret)
        val result = service.authenticateClient(params, request)
        assertEquals("client-1", result.clientId)
    }

    @Test
    fun `authenticateClient succeeds with Basic auth header`() {
        val credentials = Base64.getEncoder().encodeToString("client-1:my-secret".toByteArray())
        whenever(request.getHeader("Authorization")).thenReturn("Basic $credentials")
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val result = service.authenticateClient(emptyMap(), request)
        assertEquals("client-1", result.clientId)
    }

    @Test
    fun `authenticateClient throws InvalidClientException for wrong secret`() {
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val params = mapOf("client_id" to "client-1", "client_secret" to "wrong")
        assertThrows<InvalidClientException> { service.authenticateClient(params, request) }
    }

    @Test
    fun `authenticateClient throws InvalidClientException for unknown client`() {
        whenever(clientRepository.findById("unknown")).thenReturn(Optional.empty())
        val params = mapOf("client_id" to "unknown", "client_secret" to secret)
        assertThrows<InvalidClientException> { service.authenticateClient(params, request) }
    }

    @Test
    fun `issue throws UnsupportedGrantTypeException when no handler matches`() {
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val params = mapOf("client_id" to "client-1", "client_secret" to secret, "grant_type" to "authorization_code")
        assertThrows<UnsupportedGrantTypeException> { service.issue(params, request) }
    }
}
