package org.iamsso.services.authservice.controller

import com.nimbusds.jwt.JWTClaimsSet
import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class IntrospectionControllerTest {

    @Mock lateinit var tokenService: TokenService
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var mockMvc: MockMvc

    private val client = OAuthClientEntity(
        clientId = "client-1", clientName = "Test", clientSecretHash = "h",
        grantTypes = "", redirectUris = "", scopes = "",
        accessTokenTtlSeconds = 0, refreshTokenTtlSeconds = 0,
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(IntrospectionController(tokenService, jwtIssuer, refreshTokenRepository))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
    }

    @Test
    fun `POST introspect returns active true for valid JWT`() {
        val claims = JWTClaimsSet.Builder()
            .subject("user-1")
            .audience("client-1")
            .issuer("http://localhost:8080")
            .claim("scope", "openid")
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .build()
        whenever(jwtIssuer.verify("valid-jwt")).thenReturn(claims)

        mockMvc.post("/oauth2/introspect") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", "valid-jwt")
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(true) }
            jsonPath("$.sub") { value("user-1") }
        }
    }

    @Test
    fun `POST introspect returns active false for invalid JWT`() {
        whenever(jwtIssuer.verify(any())).thenReturn(null)
        whenever(refreshTokenRepository.findByTokenHash(any())).thenReturn(null)

        mockMvc.post("/oauth2/introspect") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", "garbage")
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(false) }
        }
    }

    @Test
    fun `POST introspect returns active true for active refresh token`() {
        whenever(jwtIssuer.verify(any())).thenReturn(null)
        val rawToken = "rt-token"
        val hash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
        )
        val entity = RefreshTokenEntity(
            tokenHash = hash, userId = UUID.randomUUID(), clientId = "client-1",
            scopes = "openid", sessionId = null,
            expiresAt = Instant.now().plusSeconds(3600),
        )
        whenever(refreshTokenRepository.findByTokenHash(hash)).thenReturn(entity)

        mockMvc.post("/oauth2/introspect") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", rawToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(true) }
        }
    }
}
