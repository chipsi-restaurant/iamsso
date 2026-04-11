package org.iamsso.services.auditservice.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.iamsso.services.auditservice.dto.AuditEventResponse
import org.iamsso.services.auditservice.entity.AuditEventEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class AuditEventMapper(private val objectMapper: ObjectMapper) {

    fun fromJson(json: String): AuditEventEntity? = try {
        val node = objectMapper.readTree(json)
        val type = node.get("type")?.asText() ?: node.get("eventType")?.asText() ?: return null
        val source = node.get("source")?.asText() ?: "unknown"
        val timeStr = node.get("time")?.asText()
        val timestamp = if (timeStr != null) Instant.parse(timeStr) else Instant.now()
        val data: JsonNode = node.get("data") ?: node

        val userId = data.get("userId")?.asText()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val clientId = data.get("clientId")?.asText()
        val sessionId = data.get("sessionId")?.asText()
        val ipAddress = data.get("ip")?.asText() ?: data.get("ipAddress")?.asText()

        @Suppress("UNCHECKED_CAST")
        val detailsMap = runCatching {
            objectMapper.convertValue(data, Map::class.java) as Map<String, Any>
        }.getOrNull()

        AuditEventEntity(
            eventType = type,
            eventSource = source,
            userId = userId,
            clientId = clientId,
            sessionId = sessionId,
            ipAddress = ipAddress,
            details = detailsMap,
            timestamp = timestamp,
        )
    } catch (_: Exception) {
        null
    }

    fun toResponse(e: AuditEventEntity) = AuditEventResponse(
        id = e.id,
        eventType = e.eventType,
        eventSource = e.eventSource,
        userId = e.userId,
        clientId = e.clientId,
        sessionId = e.sessionId,
        ipAddress = e.ipAddress,
        details = e.details,
        timestamp = e.timestamp,
    )
}
