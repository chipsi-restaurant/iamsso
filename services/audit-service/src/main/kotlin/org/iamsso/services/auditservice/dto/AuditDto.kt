package org.iamsso.services.auditservice.dto

import java.time.Instant
import java.util.UUID

data class AuditEventResponse(
    val id: UUID,
    val eventType: String,
    val eventSource: String,
    val userId: UUID? = null,
    val clientId: String? = null,
    val sessionId: String? = null,
    val ipAddress: String? = null,
    val details: Map<String, Any>? = null,
    val timestamp: Instant,
)

data class AuditStatsResponse(
    val total: Long,
    val byType: Map<String, Long>,
    val bySource: Map<String, Long>,
)
