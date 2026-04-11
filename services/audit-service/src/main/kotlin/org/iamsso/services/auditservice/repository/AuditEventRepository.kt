package org.iamsso.services.auditservice.repository

import org.iamsso.services.auditservice.entity.AuditEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AuditEventRepository : JpaRepository<AuditEventEntity, UUID>, JpaSpecificationExecutor<AuditEventEntity>

object AuditEventSpecs {
    fun withFilters(
        userId: UUID?,
        eventType: String?,
        eventSource: String?,
        from: Instant?,
        to: Instant?,
    ): Specification<AuditEventEntity> = Specification { root, _, cb ->
        val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
        if (userId != null) predicates += cb.equal(root.get<UUID>("userId"), userId)
        if (eventType != null) predicates += cb.equal(root.get<String>("eventType"), eventType)
        if (eventSource != null) predicates += cb.equal(root.get<String>("eventSource"), eventSource)
        if (from != null) predicates += cb.greaterThanOrEqualTo(root.get("timestamp"), from)
        if (to != null) predicates += cb.lessThanOrEqualTo(root.get("timestamp"), to)
        cb.and(*predicates.toTypedArray())
    }
}
