package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.UpdateProfileRequest
import org.iamsso.contracts.user.model.UserProfileResponse
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.service.UserProfileService
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileControllerTest {

    @Mock lateinit var userProfileService: UserProfileService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val sampleProfile = UserProfileResponse(
        userId = userId, displayName = "Alice",
        firstName = "Alice", locale = "en", updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(UserProfileController(userProfileService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `GET api v1 users userId profile returns 200`() {
        whenever(userProfileService.get(userId)).thenReturn(sampleProfile)

        mockMvc.get("/api/v1/users/$userId/profile")
            .andExpect {
                status { isOk() }
                jsonPath("$.displayName") { value("Alice") }
            }
    }

    @Test
    fun `PATCH api v1 users userId profile returns 200`() {
        whenever(userProfileService.update(any(), any())).thenReturn(sampleProfile)

        mockMvc.patch("/api/v1/users/$userId/profile") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(UpdateProfileRequest(displayName = "Alice"))
        }.andExpect {
            status { isOk() }
        }
    }
}
