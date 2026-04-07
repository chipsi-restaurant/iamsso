package org.iamsso.services.policyservice.repository

import org.iamsso.services.policyservice.entity.PolicyEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PolicyRepository : JpaRepository<PolicyEntity, UUID> {
    fun findAllByRoleAndEnabledTrue(role: String): List<PolicyEntity>
    fun existsByName(name: String): Boolean
    fun existsByRole(role: String): Boolean
}
