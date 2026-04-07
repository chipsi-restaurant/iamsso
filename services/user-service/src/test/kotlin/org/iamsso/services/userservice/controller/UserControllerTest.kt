package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.ChangeStatusRequest
import org.iamsso.contracts.user.model.CreateUserRequest
import org.iamsso.contracts.user.model.UpdateUserRequest
import org.iamsso.contracts.user.model.UserResponse
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.service.UserService
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerTest {

    @Mock lateinit var userService: UserService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val sampleUser = UserResponse(
        id = userId, email = "a@b.com", username = "alice",
        status = UserStatus.ACTIVE, emailVerified = true, mfaEnabled = false,
        locale = "en", createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(UserController(userService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST api v1 users returns 201 with UserResponse`() {
        val request = CreateUserRequest(password = "pass1234", email = "a@b.com")
        whenever(userService.create(any())).thenReturn(sampleUser)

        mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("a@b.com") }
        }
    }

    @Test
    fun `GET api v1 users userId returns 200`() {
        whenever(userService.getById(userId)).thenReturn(sampleUser)

        mockMvc.get("/api/v1/users/$userId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(userId.toString()) }
            }
    }

    @Test
    fun `GET api v1 users userId not found returns 404`() {
        whenever(userService.getById(userId)).thenThrow(UserNotFoundException(userId))

        mockMvc.get("/api/v1/users/$userId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("USER_NOT_FOUND") }
            }
    }

    @Test
    fun `PATCH api v1 users userId returns 200`() {
        whenever(userService.update(any(), any())).thenReturn(sampleUser)

        mockMvc.patch("/api/v1/users/$userId") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(UpdateUserRequest(displayName = "Alice"))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `DELETE api v1 users userId returns 204`() {
        mockMvc.delete("/api/v1/users/$userId")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `PUT api v1 users userId status returns 200`() {
        val request = ChangeStatusRequest(status = UserStatus.SUSPENDED, reason = "admin decision")
        whenever(userService.changeStatus(any(), any())).thenReturn(sampleUser)

        mockMvc.put("/api/v1/users/$userId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }
}
