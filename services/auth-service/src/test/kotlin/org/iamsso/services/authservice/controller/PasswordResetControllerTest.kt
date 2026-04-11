package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.service.UserServiceClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver

class PasswordResetControllerTest {

    private val userServiceClient: UserServiceClient = mock()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = PasswordResetController(userServiceClient)
        val viewResolver = InternalResourceViewResolver().apply {
            setPrefix("/WEB-INF/views/")
            setSuffix(".jsp")
        }
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(viewResolver)
            .build()
    }

    @Test
    fun `GET forgot-password without sent param renders form`() {
        mockMvc.perform(get("/forgot-password"))
            .andExpect(status().isOk)
            .andExpect(view().name("forgot-password"))
            .andExpect(model().attribute("sent", false))
    }

    @Test
    fun `POST forgot-password calls client and redirects with sent=1`() {
        mockMvc.perform(post("/forgot-password").param("email", "user@example.com"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/forgot-password?sent=1"))

        verify(userServiceClient).requestPasswordReset("user@example.com")
    }

    @Test
    fun `GET reset-password with valid token renders form`() {
        whenever(userServiceClient.validatePasswordResetToken("abc")).thenReturn(true)

        mockMvc.perform(get("/reset-password").param("token", "abc"))
            .andExpect(status().isOk)
            .andExpect(view().name("reset-password"))
            .andExpect(model().attribute("token", "abc"))
            .andExpect(model().attributeDoesNotExist("error"))
    }

    @Test
    fun `GET reset-password with invalid token sets error`() {
        whenever(userServiceClient.validatePasswordResetToken("bad")).thenReturn(false)

        mockMvc.perform(get("/reset-password").param("token", "bad"))
            .andExpect(status().isOk)
            .andExpect(model().attribute("error", "token_invalid"))
    }

    @Test
    fun `POST reset-password happy path renders success view`() {
        whenever(userServiceClient.confirmPasswordReset("tok", "NewSecureP@ss1")).thenReturn(null)

        mockMvc.perform(
            post("/reset-password")
                .param("token", "tok")
                .param("newPassword", "NewSecureP@ss1")
                .param("confirmPassword", "NewSecureP@ss1")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("reset-password"))
            .andExpect(model().attribute("success", true))
    }

    @Test
    fun `POST reset-password with mismatched passwords shows error`() {
        mockMvc.perform(
            post("/reset-password")
                .param("token", "tok")
                .param("newPassword", "NewSecureP@ss1")
                .param("confirmPassword", "other")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("reset-password"))
            .andExpect(model().attribute("error", "mismatch"))
    }

    @Test
    fun `POST reset-password with weak password shows error`() {
        whenever(userServiceClient.confirmPasswordReset("tok", "weak")).thenReturn("INVALID_PASSWORD")

        mockMvc.perform(
            post("/reset-password")
                .param("token", "tok")
                .param("newPassword", "weak")
                .param("confirmPassword", "weak")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("reset-password"))
            .andExpect(model().attribute("error", "weak_password"))
    }
}
