package org.iamsso.services.policyservice.repository

import org.iamsso.services.policyservice.entity.RoleEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoleRepository : JpaRepository<RoleEntity, UUID> {
    fun existsByName(name: String): Boolean
    fun findByName(name: String): RoleEntity?
}
