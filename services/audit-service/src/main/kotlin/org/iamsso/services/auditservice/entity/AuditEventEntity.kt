package org.iamsso.services.auditservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_events", schema = "iamsso_audit")
class AuditEventEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "event_type", nullable = false, length = 100) var eventType: String = "",
    @Column(name = "event_source", nullable = false, length = 50) var eventSource: String = "",
    @Column(name = "user_id") var userId: UUID? = null,
    @Column(name = "client_id", length = 100) var clientId: String? = null,
    @Column(name = "session_id", length = 100) var sessionId: String? = null,
    @Column(name = "ip_address", length = 50) var ipAddress: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var details: Map<String, Any>? = null,
    @Column(nullable = false) var timestamp: Instant = Instant.now(),
)
