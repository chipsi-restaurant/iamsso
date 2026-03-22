package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.AuthRequestService
import org.iamsso.services.authservice.service.SsoSessionService
import org.iamsso.services.authservice.service.UserServiceClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class AuthorizationControllerTest {

    @Mock lateinit var clientRepository: OAuthClientRepository
    @Mock lateinit var authRequestService: AuthRequestService
    @Mock lateinit var ssoSessionService: SsoSessionService
    @Mock lateinit var authCodeStore: AuthorizationCodeStore
    @Mock lateinit var userServiceClient: UserServiceClient
    @Mock lateinit var authEventPublisher: AuthEventPublisher

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = AuthorizationController(
            clientRepository = clientRepository,
            authRequestService = authRequestService,
            ssoSessionService = ssoSessionService,
            authCodeStore = authCodeStore,
            userServiceClient = userServiceClient,
            authEventPublisher = authEventPublisher,
            props = AppProperties(),
        )
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET authorize missing client_id returns 400`() {
        mockMvc.get("/oauth2/authorize") {
            param("response_type", "code")
            param("redirect_uri", "https://app.example.com/cb")
            param("code_challenge", "abc")
            param("code_challenge_method", "S256")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `POST callback with missing auth_request_id returns 400`() {
        whenever(authRequestService.getAndDelete("missing")).thenReturn(null)
        mockMvc.post("/oauth2/authorize/callback") {
            contentType = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
            param("auth_request_id", "missing")
            param("username", "user@example.com")
            param("password", "secret")
        }.andExpect { status { isBadRequest() } }
    }
}
