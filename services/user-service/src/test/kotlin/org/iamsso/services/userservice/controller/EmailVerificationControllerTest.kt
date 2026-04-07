package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.UserResponse
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.exception.InvalidVerificationTokenException
import org.iamsso.services.userservice.exception.RateLimitExceededException
import org.iamsso.services.userservice.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationControllerTest {

    @Mock lateinit var userService: UserService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val verifiedUser = UserResponse(
        id = userId, email = "a@b.com", status = UserStatus.ACTIVE,
        emailVerified = true, mfaEnabled = false, locale = "en",
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(EmailVerificationController(userService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `GET verify-email with valid token returns 200 UserResponse`() {
        whenever(userService.verifyEmail(eq(userId), eq("valid-token"))).thenReturn(verifiedUser)

        mockMvc.get("/api/v1/users/$userId/verify-email") {
            param("token", "valid-token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.emailVerified") { value(true) }
        }
    }

    @Test
    fun `GET verify-email with invalid token returns 400 INVALID_VERIFICATION_TOKEN`() {
        whenever(userService.verifyEmail(any(), eq("bad-token")))
            .thenThrow(InvalidVerificationTokenException())

        mockMvc.get("/api/v1/users/$userId/verify-email") {
            param("token", "bad-token")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_VERIFICATION_TOKEN") }
        }
    }

    @Test
    fun `POST verify-email resend returns 204`() {
        mockMvc.post("/api/v1/users/$userId/verify-email/resend")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `POST verify-email resend returns 429 when rate limit exceeded`() {
        whenever(userService.resendEmailVerification(userId))
            .thenThrow(RateLimitExceededException(30L))

        mockMvc.post("/api/v1/users/$userId/verify-email/resend")
            .andExpect {
                status { isTooManyRequests() }
                jsonPath("$.code") { value("RATE_LIMIT_EXCEEDED") }
            }
    }
}
