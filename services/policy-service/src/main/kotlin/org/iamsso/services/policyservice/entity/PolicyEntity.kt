package org.iamsso.services.policyservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "policies", schema = "iamsso_policies")
class PolicyEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(unique = true, nullable = false, length = 255) var name: String = "",
    @Column(length = 255) var description: String? = null,
    @Column(nullable = false, length = 50) var role: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) var effect: PolicyEffect = PolicyEffect.ALLOW,
    @Column(nullable = false, length = 50) var action: String = "",
    @Column(name = "resource_pattern", nullable = false, length = 255) var resourcePattern: String = "",
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") var conditions: List<PolicyCondition>? = null,
    @Column(nullable = false) var priority: Int = 0,
    @Column(nullable = false) var enabled: Boolean = true,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun onUpdate() { updatedAt = Instant.now() }
}

enum class PolicyEffect { ALLOW, DENY }

data class PolicyCondition(
    val field: String = "",
    val operator: String = "==",
    val value: String = "",
)
