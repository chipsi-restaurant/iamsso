package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.EnrollMfaRequest
import org.iamsso.contracts.user.model.MfaEnrollmentResponse
import org.iamsso.contracts.user.model.MfaFactorResponse
import org.iamsso.contracts.user.model.MfaFactorStatus
import org.iamsso.contracts.user.model.MfaFactorType
import org.iamsso.contracts.user.model.MfaStatusResponse
import org.iamsso.contracts.user.model.VerifyMfaCodeRequest
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.exception.InvalidMfaCodeException
import org.iamsso.services.userservice.service.MfaService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaControllerTest {

    @Mock lateinit var mfaService: MfaService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val factorId = UUID.randomUUID()
    private val sampleFactor = MfaFactorResponse(
        id = factorId, factorType = MfaFactorType.TOTP,
        status = MfaFactorStatus.ACTIVE, createdAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(MfaController(mfaService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `GET mfa factors returns 200 list`() {
        whenever(mfaService.listFactors(userId)).thenReturn(listOf(sampleFactor))

        mockMvc.get("/api/v1/users/$userId/mfa/factors")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].factorType") { value("TOTP") }
            }
    }

    @Test
    fun `POST mfa factors returns 201 with MfaEnrollmentResponse`() {
        val enrollment = MfaEnrollmentResponse(
            factorId = factorId, factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.PENDING, secret = "SECRET",
        )
        whenever(mfaService.enroll(any(), any())).thenReturn(enrollment)

        mockMvc.post("/api/v1/users/$userId/mfa/factors") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(EnrollMfaRequest(factorType = MfaFactorType.TOTP))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.secret") { value("SECRET") }
        }
    }

    @Test
    fun `POST mfa factors factorId verify valid code returns 200`() {
        whenever(mfaService.confirm(any(), any(), any())).thenReturn(sampleFactor)

        mockMvc.post("/api/v1/users/$userId/mfa/factors/$factorId/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(VerifyMfaCodeRequest(code = "123456"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `POST mfa factors factorId verify invalid code returns 400`() {
        whenever(mfaService.confirm(any(), any(), any())).thenThrow(InvalidMfaCodeException())

        mockMvc.post("/api/v1/users/$userId/mfa/factors/$factorId/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(VerifyMfaCodeRequest(code = "000000"))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_MFA_CODE") }
        }
    }

    @Test
    fun `DELETE mfa factors factorId returns 204`() {
        mockMvc.delete("/api/v1/users/$userId/mfa/factors/$factorId")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `GET mfa status returns 200 with MfaStatusResponse`() {
        whenever(mfaService.getStatus(userId)).thenReturn(
            MfaStatusResponse(userId = userId, mfaEnabled = false, activeFactors = emptyList())
        )

        mockMvc.get("/api/v1/users/$userId/mfa/status")
            .andExpect {
                status { isOk() }
                jsonPath("$.mfaEnabled") { value(false) }
            }
    }
}
