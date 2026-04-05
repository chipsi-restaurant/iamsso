package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidScopeException
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.repository.DeviceCodeStore
import org.iamsso.services.authservice.service.TokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.quality.Strictness
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceControllerTest {

    @Mock lateinit var tokenService: TokenService
    @Mock lateinit var deviceCodeStore: DeviceCodeStore

    private lateinit var mockMvc: MockMvc
    private val props = AppProperties()

    private val client = OAuthClientEntity(
        clientId = "client-1", clientName = "Test", clientSecretHash = "h",
        grantTypes = "urn:ietf:params:oauth:grant-type:device_code",
        redirectUris = "", scopes = "openid,email",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(DeviceController(tokenService, deviceCodeStore, props))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
    }

    @Test
    fun `POST device_authorization returns device_code and user_code`() {
        mockMvc.post("/oauth2/device_authorization") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("scope", "openid")
        }.andExpect {
            status { isOk() }
            jsonPath("$.device_code") { exists() }
            jsonPath("$.user_code") { exists() }
            jsonPath("$.verification_uri") { exists() }
            jsonPath("$.expires_in") { value(600) }
            jsonPath("$.interval") { value(5) }
        }
    }

    @Test
    fun `POST device_authorization returns 400 for invalid scope`() {
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
        mockMvc.post("/oauth2/device_authorization") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("scope", "admin")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_scope") }
        }
    }
}
