package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.JwtKeyProvider
import org.iamsso.services.authservice.service.UserServiceClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class UserInfoControllerTest {

    @Mock lateinit var userServiceClient: UserServiceClient
    @Mock lateinit var jwtKeyProvider: JwtKeyProvider

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = UserInfoController(jwtKeyProvider, userServiceClient)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET userinfo without token returns 401`() {
        mockMvc.get("/userinfo")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `POST userinfo without token returns 401`() {
        mockMvc.post("/userinfo") {}
            .andExpect { status { isUnauthorized() } }
    }
}
