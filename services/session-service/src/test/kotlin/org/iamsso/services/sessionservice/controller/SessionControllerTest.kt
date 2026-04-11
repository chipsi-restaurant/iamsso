package org.iamsso.services.sessionservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.sessionservice.dto.CreateSessionRequest
import org.iamsso.services.sessionservice.model.SsoSession
import org.iamsso.services.sessionservice.service.SsoSessionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
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
class SessionControllerTest {

    @Mock lateinit var service: SsoSessionService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val now = Instant.parse("2026-04-07T12:00:00Z")
    private val sampleSession = SsoSession(
        sessionId = "abc-123",
        userId = userId,
        clientIds = mutableListOf("client-1"),
        createdAt = now,
        lastActivityAt = now,
        expiresAt = now.plusSeconds(3600),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(SessionController(service))
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST api v1 sessions creates session and returns 201`() {
        val request = CreateSessionRequest(userId = userId, clientId = "client-1")
        whenever(service.create(eq(userId), eq("client-1"))).thenReturn(sampleSession)

        mockMvc.post("/api/v1/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.sessionId") { value("abc-123") }
            jsonPath("$.userId") { value(userId.toString()) }
            jsonPath("$.clientIds[0]") { value("client-1") }
        }
    }

    @Test
    fun `GET api v1 sessions sessionId returns 200 when found`() {
        whenever(service.get("abc-123")).thenReturn(sampleSession)

        mockMvc.get("/api/v1/sessions/abc-123")
            .andExpect {
                status { isOk() }
                jsonPath("$.sessionId") { value("abc-123") }
                jsonPath("$.userId") { value(userId.toString()) }
            }
    }

    @Test
    fun `GET api v1 sessions sessionId returns 404 when not found`() {
        whenever(service.get("missing")).thenReturn(null)

        mockMvc.get("/api/v1/sessions/missing")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE api v1 sessions sessionId returns 204`() {
        mockMvc.delete("/api/v1/sessions/abc-123")
            .andExpect { status { isNoContent() } }

        verify(service).delete("abc-123", "logout")
    }
}
