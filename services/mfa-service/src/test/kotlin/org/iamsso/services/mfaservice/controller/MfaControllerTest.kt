package org.iamsso.services.mfaservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.mfa.model.ConfirmMfaRequest
import org.iamsso.contracts.mfa.model.EnrollMfaFactorRequest
import org.iamsso.contracts.mfa.model.MfaEnrollmentResponse
import org.iamsso.contracts.mfa.model.MfaFactorResponse
import org.iamsso.contracts.mfa.model.MfaFactorStatus
import org.iamsso.contracts.mfa.model.MfaFactorType
import org.iamsso.contracts.mfa.model.MfaStatusResponse
import org.iamsso.contracts.mfa.model.VerifyMfaRequest
import org.iamsso.contracts.mfa.model.VerifyMfaResponse
import org.iamsso.services.mfaservice.exception.GlobalExceptionHandler
import org.iamsso.services.mfaservice.exception.InvalidMfaCodeException
import org.iamsso.services.mfaservice.service.MfaService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MfaControllerTest {

    @Mock
    lateinit var mfaService: MfaService

    @InjectMocks
    lateinit var mfaController: MfaController

    lateinit var mockMvc: MockMvc
    lateinit var objectMapper: ObjectMapper

    private val userId = UUID.randomUUID()
    private val factorId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule {
                enable(KotlinFeature.NullIsSameAsDefault)
            })
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        val converter = MappingJackson2HttpMessageConverter(objectMapper)
        mockMvc = MockMvcBuilders.standaloneSetup(mfaController)
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(converter)
            .build()
    }

    @Test
    fun `GET factors returns 200 list`() {
        val response = MfaFactorResponse(
            id = factorId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE,
            displayName = "My TOTP",
            createdAt = Instant.now(),
        )
        whenever(mfaService.listFactors(userId)).thenReturn(listOf(response))

        mockMvc.perform(get("/api/v1/mfa/$userId/factors"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(factorId.toString()))
            .andExpect(jsonPath("$[0].factorType").value("TOTP"))
    }

    @Test
    fun `POST factors returns 201`() {
        val request = EnrollMfaFactorRequest(factorType = MfaFactorType.TOTP, displayName = "My TOTP")
        val response = MfaEnrollmentResponse(
            factorId = factorId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.PENDING,
            secret = "SECRET",
            provisioningUri = "otpauth://totp/IAMSSO:user@test.com?secret=SECRET&issuer=IAMSSO",
        )
        whenever(mfaService.enroll(eq(userId), any(), any())).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/mfa/$userId/factors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-Email", "user@test.com"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.factorId").value(factorId.toString()))
            .andExpect(jsonPath("$.secret").value("SECRET"))
    }

    @Test
    fun `POST factors confirm returns 200`() {
        val request = ConfirmMfaRequest(code = "123456")
        val response = MfaFactorResponse(
            id = factorId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE,
            displayName = "My TOTP",
            createdAt = Instant.now(),
        )
        whenever(mfaService.confirm(userId, factorId, "123456")).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/mfa/$userId/factors/$factorId/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `POST factors confirm invalid returns 400`() {
        whenever(mfaService.confirm(eq(userId), eq(factorId), any())).thenThrow(InvalidMfaCodeException())

        mockMvc.perform(
            post("/api/v1/mfa/$userId/factors/$factorId/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ConfirmMfaRequest(code = "000000"))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_MFA_CODE"))
    }

    @Test
    fun `DELETE factors returns 204`() {
        mockMvc.perform(delete("/api/v1/mfa/$userId/factors/$factorId"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `POST verify returns 200 valid`() {
        val request = VerifyMfaRequest(code = "123456")
        whenever(mfaService.verify(eq(userId), any())).thenReturn(VerifyMfaResponse(valid = true))

        mockMvc.perform(
            post("/api/v1/mfa/$userId/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
    }

    @Test
    fun `GET status returns 200`() {
        val response = MfaStatusResponse(
            userId = userId,
            mfaEnabled = true,
            activeFactors = listOf(MfaFactorType.TOTP),
        )
        whenever(mfaService.getStatus(userId)).thenReturn(response)

        mockMvc.perform(get("/api/v1/mfa/$userId/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mfaEnabled").value(true))
            .andExpect(jsonPath("$.activeFactors[0]").value("TOTP"))
    }

    @Test
    fun `POST send-otp returns 204`() {
        mockMvc.perform(
            post("/api/v1/mfa/$userId/send-otp")
                .header("X-User-Email", "user@test.com"),
        )
            .andExpect(status().isNoContent)
    }
}
