package org.iamsso.apps.vacation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.apps.vacation.entity.VacationRequestEntity
import org.iamsso.apps.vacation.entity.VacationRequestStatus
import org.iamsso.apps.vacation.entity.VacationRequestType
import org.iamsso.apps.vacation.exception.GlobalExceptionHandler
import org.iamsso.apps.vacation.exception.NotFoundException
import org.iamsso.apps.vacation.service.VacationRequestService
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
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VacationRequestControllerTest {

    @Mock lateinit var service: VacationRequestService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()

    private val sampleEntity = VacationRequestEntity(
        userId = userId,
        type = VacationRequestType.VACATION,
        startDate = LocalDate.of(2026, 5, 1),
        endDate = LocalDate.of(2026, 5, 10),
        reason = "holiday",
        status = VacationRequestStatus.PENDING,
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(VacationRequestController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST returns 201 when user header present`() {
        whenever(service.create(any(), any())).thenReturn(sampleEntity)
        val body = mapper.writeValueAsString(CreateRequestBody(
            type = VacationRequestType.VACATION,
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 10),
            reason = "holiday",
        ))

        mockMvc.post("/api/v1/vacation-requests") {
            contentType = MediaType.APPLICATION_JSON
            header("X-User-Id", userId.toString())
            content = body
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.reason") { value("holiday") }
        }
    }

    @Test
    fun `POST returns 403 without user header`() {
        val body = mapper.writeValueAsString(CreateRequestBody(
            type = VacationRequestType.VACATION,
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 10),
            reason = "holiday",
        ))

        mockMvc.post("/api/v1/vacation-requests") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET my returns 200 with paged response`() {
        whenever(service.findMyRequests(any(), any(), any()))
            .thenReturn(PageImpl(listOf(sampleEntity)))

        mockMvc.get("/api/v1/vacation-requests/my") {
            header("X-User-Id", userId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.totalElements") { value(1) }
        }
    }

    @Test
    fun `GET all returns 403 for non-manager`() {
        mockMvc.get("/api/v1/vacation-requests") {
            header("X-User-Id", userId.toString())
            header("X-User-Role", "user")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET all returns 200 for moderator`() {
        whenever(service.findAll(anyOrNull(), any(), any()))
            .thenReturn(PageImpl(listOf(sampleEntity)))

        mockMvc.get("/api/v1/vacation-requests") {
            header("X-User-Id", userId.toString())
            header("X-User-Role", "moderator")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `approve returns 200 for moderator`() {
        whenever(service.approve(any(), any(), anyOrNull())).thenReturn(
            sampleEntity.apply { status = VacationRequestStatus.APPROVED }
        )

        mockMvc.post("/api/v1/vacation-requests/${sampleEntity.id}/approve") {
            contentType = MediaType.APPLICATION_JSON
            header("X-User-Id", userId.toString())
            header("X-User-Role", "moderator")
            content = "{\"comment\":\"ok\"}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
        }
    }

    @Test
    fun `approve returns 403 for non-manager`() {
        mockMvc.post("/api/v1/vacation-requests/${sampleEntity.id}/approve") {
            header("X-User-Id", userId.toString())
            header("X-User-Role", "user")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `cancel returns 200 for owner`() {
        whenever(service.cancel(any(), any())).thenReturn(
            sampleEntity.apply { status = VacationRequestStatus.CANCELLED }
        )

        mockMvc.post("/api/v1/vacation-requests/${sampleEntity.id}/cancel") {
            header("X-User-Id", userId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELLED") }
        }
    }

    @Test
    fun `GET by id returns 404 when not found`() {
        val missing = UUID.randomUUID()
        whenever(service.getById(any(), any(), any())).thenThrow(NotFoundException("vacation-request", missing))

        mockMvc.get("/api/v1/vacation-requests/$missing") {
            header("X-User-Id", userId.toString())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }
}
