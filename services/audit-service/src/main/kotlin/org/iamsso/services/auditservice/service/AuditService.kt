package org.iamsso.services.auditservice.service

import org.iamsso.services.auditservice.dto.AuditEventResponse
import org.iamsso.services.auditservice.dto.AuditStatsResponse
import org.iamsso.services.auditservice.repository.AuditEventRepository
import org.iamsso.services.auditservice.repository.AuditEventSpecs
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
        val pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "timestamp"))
        val spec = AuditEventSpecs.withFilters(userId, eventType, eventSource, from, to)
        return repo.findAll(spec, pageable).map { mapper.toResponse(it) }
    }

    fun getById(id: UUID): AuditEventResponse? =
        repo.findById(id).map { mapper.toResponse(it) }.orElse(null)

    fun getStats(from: Instant?, to: Instant?): AuditStatsResponse {
        val spec = AuditEventSpecs.withFilters(null, null, null, from, to)
        val all = repo.findAll(spec)
        val byType = all.groupingBy { it.eventType }.eachCount().mapValues { it.value.toLong() }
        val bySource = all.groupingBy { it.eventSource }.eachCount().mapValues { it.value.toLong() }
        return AuditStatsResponse(
            total = all.size.toLong(),
            byType = byType,
            bySource = bySource,
        )
    }
}
