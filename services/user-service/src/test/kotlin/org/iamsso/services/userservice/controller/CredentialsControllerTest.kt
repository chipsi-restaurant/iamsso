package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.ChangePasswordRequest
import org.iamsso.contracts.user.model.CredentialsVerificationResponse
import org.iamsso.contracts.user.model.ResetPasswordRequest
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.contracts.user.model.VerifyCredentialsRequest
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.service.CredentialsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredentialsControllerTest {

    @Mock lateinit var credentialsService: CredentialsService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(CredentialsController(credentialsService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST credentials verify returns 200 with CredentialsVerificationResponse`() {
        val response = CredentialsVerificationResponse(valid = true, userId = userId,
            status = UserStatus.ACTIVE, mfaRequired = false, failedAttempts = 0)
        whenever(credentialsService.verify(any(), any(), anyOrNull(), anyOrNull())).thenReturn(response)

        mockMvc.post("/api/v1/users/$userId/credentials/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(VerifyCredentialsRequest(password = "pass"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
        }
    }

    @Test
    fun `PUT credentials password returns 204`() {
        mockMvc.put("/api/v1/users/$userId/credentials/password") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(
                ChangePasswordRequest(currentPassword = "old", newPassword = "new"))
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `POST credentials password reset returns 204`() {
        mockMvc.post("/api/v1/users/$userId/credentials/password/reset") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(ResetPasswordRequest(newPassword = "new"))
        }.andExpect {
            status { isNoContent() }
        }
    }
}
