package org.iamsso.services.auditservice.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.auditservice.entity.AuditEventEntity
import org.iamsso.services.auditservice.repository.AuditEventRepository
import org.iamsso.services.auditservice.service.AuditEventMapper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AuditKafkaConsumerTest {

    @Mock lateinit var repo: AuditEventRepository

    private val objectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
    }
    private val mapper = AuditEventMapper(objectMapper)
    private lateinit var consumer: AuditKafkaConsumer

    @BeforeEach
    fun setUp() {
        consumer = AuditKafkaConsumer(repo, mapper)
    }

    @Test
    fun `onEvent parses JSON and saves entity`() {
        val userId = UUID.randomUUID()
        val json = """
            {
              "id":"${UUID.randomUUID()}",
              "source":"user-service",
              "type":"user.created",
              "time":"2026-04-11T10:00:00Z",
              "data":{"userId":"$userId","email":"bob@example.com"}
            }
        """.trimIndent()

        consumer.onEvent(json)

        val captor = ArgumentCaptor.forClass(AuditEventEntity::class.java)
        verify(repo).save(captor.capture())
        val saved = captor.value
        assertEquals("user.created", saved.eventType)
        assertEquals("user-service", saved.eventSource)
        assertEquals(userId, saved.userId)
    }

    @Test
    fun `onEvent handles invalid JSON gracefully without throwing`() {
        assertDoesNotThrow { consumer.onEvent("{not-json") }
        verify(repo, never()).save(any())
    }
}
