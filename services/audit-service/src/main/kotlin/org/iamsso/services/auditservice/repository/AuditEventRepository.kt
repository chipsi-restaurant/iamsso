package org.iamsso.services.auditservice.repository

import org.iamsso.services.auditservice.entity.AuditEventEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AuditEventRepository : JpaRepository<AuditEventEntity, UUID> {

    @Query("""
        SELECT e FROM AuditEventEntity e
        WHERE (:userId IS NULL OR e.userId = :userId)
        AND (:eventType IS NULL OR e.eventType = :eventType)
        AND (:eventSource IS NULL OR e.eventSource = :eventSource)
        AND (:from IS NULL OR e.timestamp >= :from)
        AND (:to IS NULL OR e.timestamp <= :to)
        ORDER BY e.timestamp DESC
    """)
    fun search(
        @Param("userId") userId: UUID?,
        @Param("eventType") eventType: String?,
        @Param("eventSource") eventSource: String?,
        @Param("from") from: Instant?,
        @Param("to") to: Instant?,
        pageable: Pageable,
    ): Page<AuditEventEntity>

    @Query("""
        SELECT e.eventType as eventType, COUNT(e) as count
        FROM AuditEventEntity e
        WHERE (:from IS NULL OR e.timestamp >= :from)
        AND (:to IS NULL OR e.timestamp <= :to)
        GROUP BY e.eventType
    """)
    fun countByType(@Param("from") from: Instant?, @Param("to") to: Instant?): List<Array<Any>>

    @Query("""
        SELECT e.eventSource as eventSource, COUNT(e) as count
        FROM AuditEventEntity e
        WHERE (:from IS NULL OR e.timestamp >= :from)
        AND (:to IS NULL OR e.timestamp <= :to)
        GROUP BY e.eventSource
    """)
    fun countBySource(@Param("from") from: Instant?, @Param("to") to: Instant?): List<Array<Any>>
}
