package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.SsoSessionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class SessionControllerTest {

    @Mock lateinit var ssoSessionService: SsoSessionService
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var authEventPublisher: AuthEventPublisher

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = SessionController(ssoSessionService, refreshTokenRepository, authEventPublisher)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET current without SSO_SESSION cookie returns 401`() {
        mockMvc.get("/api/v1/sessions/current")
            .andExpect { status { isUnauthorized() } }
    }
}
