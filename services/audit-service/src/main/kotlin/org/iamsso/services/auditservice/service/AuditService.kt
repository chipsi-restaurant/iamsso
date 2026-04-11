package org.iamsso.services.auditservice.service

import org.iamsso.services.auditservice.dto.AuditEventResponse
import org.iamsso.services.auditservice.dto.AuditStatsResponse
import org.iamsso.services.auditservice.repository.AuditEventRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AuditService(
    private val repo: AuditEventRepository,
    private val mapper: AuditEventMapper,
) {

    fun search(
        userId: UUID?,
        eventType: String?,
        eventSource: String?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
    ): Page<AuditEventResponse> {
        val clampedSize = size.coerceIn(1, 200)
        val pageable = PageRequest.of(page, clampedSize)
        return repo.search(userId, eventType, eventSource, from, to, pageable)
            .map { mapper.toResponse(it) }
    }

    fun getById(id: UUID): AuditEventResponse? =
        repo.findById(id).map { mapper.toResponse(it) }.orElse(null)

    fun getStats(from: Instant?, to: Instant?): AuditStatsResponse {
        val byType = repo.countByType(from, to).associate { it[0] as String to (it[1] as Long) }
        val bySource = repo.countBySource(from, to).associate { it[0] as String to (it[1] as Long) }
        return AuditStatsResponse(
            total = byType.values.sum(),
            byType = byType,
            bySource = bySource,
        )
    }
}
