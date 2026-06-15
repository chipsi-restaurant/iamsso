package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.hamcrest.Matchers.containsString
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.filter.CharacterEncodingFilter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationControllerTest {

    @Mock
    lateinit var userService: UserService

    private lateinit var mockMvc: MockMvc

    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private lateinit var userId: UUID
    private lateinit var verifiedUser: UserResponse

    @BeforeEach
    fun setUp() {
        userId = UUID.randomUUID()

        verifiedUser = UserResponse(
            id = userId,
            email = "a@b.com",
            status = UserStatus.ACTIVE,
            emailVerified = true,
            mfaEnabled = false,
            locale = "en",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        val builder: StandaloneMockMvcBuilder = MockMvcBuilders
            .standaloneSetup(EmailVerificationController(userService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(
                StringHttpMessageConverter(StandardCharsets.UTF_8),
                MappingJackson2HttpMessageConverter(mapper),
            )
            .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)

        mockMvc = builder.build()
    }

    @Test
    fun `GET verify-email with valid token returns 200 HTML success page`() {
        whenever(userService.verifyEmail(eq(userId), eq("valid-token")))
            .thenReturn(verifiedUser)

        mockMvc.get("/api/v1/users/$userId/verify-email") {
            param("token", "valid-token")
            characterEncoding = "UTF-8"
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(containsString("Email подтверждён")) }
        }

        verify(userService).verifyEmail(userId, "valid-token")
    }

    @Test
    fun `GET verify-email with invalid token returns 400 HTML error page`() {
        whenever(userService.verifyEmail(eq(userId), eq("bad-token")))
            .thenThrow(InvalidVerificationTokenException())

        mockMvc.get("/api/v1/users/$userId/verify-email") {
            param("token", "bad-token")
            characterEncoding = "UTF-8"
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(containsString("Ошибка подтверждения")) }
        }

        verify(userService).verifyEmail(userId, "bad-token")
    }

    @Test
    fun `POST verify-email resend returns 204`() {
        mockMvc.post("/api/v1/users/$userId/verify-email/resend") {
            characterEncoding = "UTF-8"
        }.andExpect {
            status { isNoContent() }
        }

        verify(userService).resendEmailVerification(userId)
    }

    @Test
    fun `POST verify-email resend returns 429 when rate limit exceeded`() {
        doThrow(RateLimitExceededException(30L))
            .whenever(userService)
            .resendEmailVerification(userId)

        mockMvc.post("/api/v1/users/$userId/verify-email/resend") {
            characterEncoding = "UTF-8"
        }.andExpect {
            status { isTooManyRequests() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.code") { value("RATE_LIMIT_EXCEEDED") }
        }

        verify(userService).resendEmailVerification(userId)
    }
}