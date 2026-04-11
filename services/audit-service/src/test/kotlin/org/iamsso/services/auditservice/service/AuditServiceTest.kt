package org.iamsso.services.auditservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.auditservice.entity.AuditEventEntity
import org.iamsso.services.auditservice.repository.AuditEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditServiceTest {

    @Mock lateinit var repo: AuditEventRepository

    private val objectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
    }
    private val mapper = AuditEventMapper(objectMapper)
    private lateinit var service: AuditService

    @BeforeEach
    fun setUp() {
        service = AuditService(repo, mapper)
    }

    private fun sampleEntity(
        eventType: String = "user.created",
        eventSource: String = "user-service",
    ) = AuditEventEntity(
        id = UUID.randomUUID(),
        eventType = eventType,
        eventSource = eventSource,
        userId = UUID.randomUUID(),
        timestamp = Instant.parse("2026-04-07T10:00:00Z"),
    )

    @Test
    fun `search calls repository with spec and returns mapped page`() {
        val userId = UUID.randomUUID()
        val from = Instant.parse("2026-04-01T00:00:00Z")
        val to = Instant.parse("2026-04-07T00:00:00Z")
        val entity = sampleEntity()
        val pageImpl = PageImpl(listOf(entity), PageRequest.of(0, 50), 1L)

        whenever(repo.findAll(any<Specification<AuditEventEntity>>(), any<Pageable>())).thenReturn(pageImpl)

        val result = service.search(userId, "user.created", "user-service", from, to, 0, 50)

        assertEquals(1, result.totalElements)
        assertEquals("user.created", result.content[0].eventType)

        val pageableCaptor = argumentCaptor<Pageable>()
        verify(repo).findAll(any<Specification<AuditEventEntity>>(), pageableCaptor.capture())
        assertEquals(0, pageableCaptor.firstValue.pageNumber)
        assertEquals(50, pageableCaptor.firstValue.pageSize)
    }

    @Test
    fun `search clamps page size to 200 when size greater than 200`() {
        whenever(repo.findAll(any<Specification<AuditEventEntity>>(), any<Pageable>()))
            .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 200), 0L))

        service.search(null, null, null, null, null, 0, 500)

        val pageableCaptor = argumentCaptor<Pageable>()
        verify(repo).findAll(any<Specification<AuditEventEntity>>(), pageableCaptor.capture())
        assertEquals(200, pageableCaptor.firstValue.pageSize)
    }

    @Test
    fun `getById returns response when found null when missing`() {
        val entity = sampleEntity()
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        val found = service.getById(entity.id)
        assertNotNull(found)
        assertEquals(entity.id, found!!.id)

        val missingId = UUID.randomUUID()
        whenever(repo.findById(missingId)).thenReturn(Optional.empty())
        assertNull(service.getById(missingId))
    }

    @Test
    fun `getStats aggregates byType and bySource computes total`() {
        val events = listOf(
            sampleEntity(eventType = "user.created", eventSource = "user-service"),
            sampleEntity(eventType = "user.created", eventSource = "user-service"),
            sampleEntity(eventType = "user.created", eventSource = "user-service"),
            sampleEntity(eventType = "user.created", eventSource = "user-service"),
            sampleEntity(eventType = "user.created", eventSource = "user-service"),
            sampleEntity(eventType = "auth.login-success", eventSource = "auth-service"),
            sampleEntity(eventType = "auth.login-success", eventSource = "auth-service"),
            sampleEntity(eventType = "auth.login-success", eventSource = "auth-service"),
        )
        whenever(repo.findAll(any<Specification<AuditEventEntity>>())).thenReturn(events)

        val stats = service.getStats(null, null)

        assertEquals(8L, stats.total)
        assertEquals(5L, stats.byType["user.created"])
        assertEquals(3L, stats.byType["auth.login-success"])
        assertEquals(5L, stats.bySource["user-service"])
        assertEquals(3L, stats.bySource["auth-service"])
    }
}
