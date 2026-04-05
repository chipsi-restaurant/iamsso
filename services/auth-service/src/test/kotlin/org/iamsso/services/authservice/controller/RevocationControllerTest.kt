package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.TokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RevocationControllerTest {

    @Mock lateinit var tokenService: TokenService
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var mockMvc: MockMvc

    private val rawToken = "my-token"
    private val tokenHash = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
    )
    private val client = OAuthClientEntity(
        clientId = "client-1", clientName = "Test", clientSecretHash = "h",
        grantTypes = "", redirectUris = "", scopes = "",
        accessTokenTtlSeconds = 0, refreshTokenTtlSeconds = 0,
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(RevocationController(tokenService, refreshTokenRepository))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
    }

    @Test
    fun `POST revoke revokes token and returns 200`() {
        val entity = RefreshTokenEntity(
            tokenHash = tokenHash, userId = UUID.randomUUID(), clientId = "client-1",
            scopes = "openid", sessionId = null,
            expiresAt = Instant.now().plusSeconds(3600),
        )
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(entity)
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }

        mockMvc.post("/oauth2/revoke") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", rawToken)
        }.andExpect { status { isOk() } }

        verify(refreshTokenRepository).save(any())
    }

    @Test
    fun `POST revoke returns 200 even when token not found (RFC 7009)`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(null)

        mockMvc.post("/oauth2/revoke") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", rawToken)
        }.andExpect { status { isOk() } }

        verify(refreshTokenRepository, never()).save(any())
    }
}
