package org.iamsso.services.policyservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "roles", schema = "iamsso_policies")
class RoleEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(unique = true, nullable = false, length = 50) var name: String = "",
    @Column(length = 255) var description: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun onUpdate() { updatedAt = Instant.now() }
}
