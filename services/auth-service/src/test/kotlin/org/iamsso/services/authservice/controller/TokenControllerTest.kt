package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.exception.UnsupportedGrantTypeException
import org.iamsso.services.authservice.service.TokenResponse
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

@ExtendWith(MockitoExtension::class)
class TokenControllerTest {

    @Mock lateinit var tokenService: TokenService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(TokenController(tokenService))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
    }

    @Test
    fun `POST token returns 200 with token response`() {
        val tokenResponse = TokenResponse(
            accessToken = "at123",
            expiresIn = 3600,
            scope = "openid",
        )
        whenever(tokenService.issue(any(), any<HttpServletRequest>())).thenReturn(tokenResponse)

        mockMvc.post("/oauth2/token") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("grant_type", "authorization_code")
            param("client_id", "client-1")
            param("client_secret", "secret")
            param("code", "some-code")
            param("code_verifier", "verifier")
            param("redirect_uri", "https://app.example.com/cb")
        }.andExpect {
            status { isOk() }
            jsonPath("$.access_token") { value("at123") }
            jsonPath("$.token_type") { value("Bearer") }
            jsonPath("$.expires_in") { value(3600) }
        }
    }

    @Test
    fun `POST token returns 400 on unsupported grant type`() {
        whenever(tokenService.issue(any(), any<HttpServletRequest>())).thenThrow(
            UnsupportedGrantTypeException("Unsupported")
        )
        mockMvc.post("/oauth2/token") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("grant_type", "implicit")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("unsupported_grant_type") }
        }
    }
}
