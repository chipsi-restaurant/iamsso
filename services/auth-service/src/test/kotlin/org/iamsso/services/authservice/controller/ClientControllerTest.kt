package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.service.ClientService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class ClientControllerTest {

    @Mock lateinit var clientService: ClientService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = ClientController(clientService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET clients returns 200`() {
        mockMvc.get("/api/v1/clients")
            .andExpect { status { isOk() } }
    }
}
