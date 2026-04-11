package org.iamsso.services.auditservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AuditEventMapperTest {

    private val objectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
    }
    private val mapper = AuditEventMapper(objectMapper)

    @Test
    fun `parse user_created event with userId and source`() {
        val userId = UUID.randomUUID()
        val json = """
            {
              "id":"${UUID.randomUUID()}",
              "source":"user-service",
              "type":"user.created",
              "time":"2026-04-11T10:00:00Z",
              "data":{"userId":"$userId","email":"alice@example.com"}
            }
        """.trimIndent()

        val entity = mapper.fromJson(json)

        assertNotNull(entity)
        assertEquals("user.created", entity!!.eventType)
        assertEquals("user-service", entity.eventSource)
        assertEquals(userId, entity.userId)
        assertEquals(Instant.parse("2026-04-11T10:00:00Z"), entity.timestamp)
        assertEquals("alice@example.com", entity.details?.get("email"))
    }

    @Test
    fun `parse auth_login-success with clientId and sessionId`() {
        val json = """
            {
              "id":"${UUID.randomUUID()}",
              "source":"auth-service",
              "type":"auth.login-success",
              "time":"2026-04-11T12:30:00Z",
              "data":{
                "clientId":"web-client",
                "sessionId":"sess-123",
                "ipAddress":"10.0.0.1"
              }
            }
        """.trimIndent()

        val entity = mapper.fromJson(json)

        assertNotNull(entity)
        assertEquals("auth.login-success", entity!!.eventType)
        assertEquals("auth-service", entity.eventSource)
        assertEquals("web-client", entity.clientId)
        assertEquals("sess-123", entity.sessionId)
        assertEquals("10.0.0.1", entity.ipAddress)
        assertNull(entity.userId)
    }

    @Test
    fun `returns null for invalid JSON`() {
        val entity = mapper.fromJson("{not-json")
        assertNull(entity)
    }

    @Test
    fun `returns null for JSON without type field`() {
        val json = """{"source":"user-service","data":{"foo":"bar"}}"""
        val entity = mapper.fromJson(json)
        assertNull(entity)
    }
}
