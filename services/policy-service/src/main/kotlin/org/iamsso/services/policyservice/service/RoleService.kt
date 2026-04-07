package org.iamsso.services.policyservice.service

import org.iamsso.services.policyservice.entity.RoleEntity
import org.iamsso.services.policyservice.exception.RoleAlreadyExistsException
import org.iamsso.services.policyservice.exception.RoleInUseException
import org.iamsso.services.policyservice.exception.RoleNotFoundException
import org.iamsso.services.policyservice.mapper.CreateRoleRequest
import org.iamsso.services.policyservice.mapper.RoleResponse
import org.iamsso.services.policyservice.mapper.toResponse
import org.iamsso.services.policyservice.repository.PolicyRepository
import org.iamsso.services.policyservice.repository.RoleRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoleService(
    private val roleRepo: RoleRepository,
    private val policyRepo: PolicyRepository,
) {
    fun list(): List<RoleResponse> = roleRepo.findAll().map { it.toResponse() }

    fun create(request: CreateRoleRequest): RoleResponse {
        if (roleRepo.existsByName(request.name)) throw RoleAlreadyExistsException(request.name)
        val entity = RoleEntity(name = request.name, description = request.description)
        roleRepo.save(entity)
        return entity.toResponse()
    }

    fun delete(id: UUID) {
        val role = roleRepo.findById(id).orElseThrow { RoleNotFoundException(id.toString()) }
        if (policyRepo.existsByRole(role.name)) throw RoleInUseException(role.name)
        roleRepo.delete(role)
    }
}
