package org.iamsso.services.auditservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.auditservice.dto.AuditEventResponse
import org.iamsso.services.auditservice.dto.AuditStatsResponse
import org.iamsso.services.auditservice.service.AuditService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditControllerTest {

    @Mock lateinit var auditService: AuditService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AuditController(auditService))
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    private fun sampleResponse(
        id: UUID = UUID.randomUUID(),
        eventType: String = "user.created",
        eventSource: String = "user-service",
        userId: UUID? = UUID.randomUUID(),
    ) = AuditEventResponse(
        id = id,
        eventType = eventType,
        eventSource = eventSource,
        userId = userId,
        timestamp = Instant.parse("2026-04-07T10:00:00Z"),
    )

    @Test
    fun `GET audit events returns 200 with page`() {
        val response = sampleResponse()
        whenever(auditService.search(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(PageImpl(listOf(response), PageRequest.of(0, 50), 1L))

        mockMvc.get("/api/v1/audit/events")
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].eventType") { value("user.created") }
            }
    }

    @Test
    fun `GET audit events with userId filter passes it to service`() {
        val userId = UUID.randomUUID()
        val response = sampleResponse(userId = userId)
        whenever(auditService.search(eq(userId), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(PageImpl(listOf(response), PageRequest.of(0, 50), 1L))

        mockMvc.get("/api/v1/audit/events") {
            param("userId", userId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].userId") { value(userId.toString()) }
        }

        val captor = argumentCaptor<UUID>()
        verify(auditService).search(captor.capture(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
        assertEquals(userId, captor.firstValue)
    }

    @Test
    fun `GET audit events by id returns 404 when service returns null`() {
        val id = UUID.randomUUID()
        whenever(auditService.getById(id)).thenReturn(null)

        mockMvc.get("/api/v1/audit/events/$id")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `GET audit stats returns 200 with aggregation`() {
        val stats = AuditStatsResponse(
            total = 8L,
            byType = mapOf("user.created" to 5L, "auth.login-success" to 3L),
            bySource = mapOf("user-service" to 5L, "auth-service" to 3L),
        )
        whenever(auditService.getStats(anyOrNull(), anyOrNull())).thenReturn(stats)

        mockMvc.get("/api/v1/audit/stats")
            .andExpect {
                status { isOk() }
                jsonPath("$.total") { value(8) }
                jsonPath("$.byType['user.created']") { value(5) }
                jsonPath("$.bySource['auth-service']") { value(3) }
            }
    }
}
