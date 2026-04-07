package org.iamsso.services.policyservice.service

import org.iamsso.services.policyservice.entity.PolicyEntity
import org.iamsso.services.policyservice.exception.PolicyAlreadyExistsException
import org.iamsso.services.policyservice.exception.PolicyNotFoundException
import org.iamsso.services.policyservice.exception.RoleNotFoundException
import org.iamsso.services.policyservice.mapper.CreatePolicyRequest
import org.iamsso.services.policyservice.mapper.PolicyResponse
import org.iamsso.services.policyservice.mapper.UpdatePolicyRequest
import org.iamsso.services.policyservice.mapper.toResponse
import org.iamsso.services.policyservice.repository.PolicyRepository
import org.iamsso.services.policyservice.repository.RoleRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PolicyService(
    private val policyRepo: PolicyRepository,
    private val roleRepo: RoleRepository,
    private val events: EventPublisher,
) {
    fun list(page: Int, size: Int): Page<PolicyResponse> =
        policyRepo.findAll(PageRequest.of(page, size)).map { it.toResponse() }

    fun getById(id: UUID): PolicyResponse =
        findOrThrow(id).toResponse()

    fun create(request: CreatePolicyRequest): PolicyResponse {
        if (policyRepo.existsByName(request.name)) throw PolicyAlreadyExistsException(request.name)
        if (!roleRepo.existsByName(request.role)) throw RoleNotFoundException(request.role)

        val entity = PolicyEntity(
            name = request.name,
            description = request.description,
            role = request.role,
            effect = request.effect,
            action = request.action,
            resourcePattern = request.resourcePattern,
            conditions = request.conditions,
            priority = request.resolvedPriority(),
            enabled = request.resolvedEnabled(),
        )
        policyRepo.save(entity)
        events.policyCreated(entity.id, entity.name, entity.role, entity.action, entity.resourcePattern)
        return entity.toResponse()
    }

    fun update(id: UUID, request: UpdatePolicyRequest): PolicyResponse {
        val entity = findOrThrow(id)
        val changed = mutableListOf<String>()

        request.name?.let {
            if (it != entity.name && policyRepo.existsByName(it)) throw PolicyAlreadyExistsException(it)
            entity.name = it; changed += "name"
        }
        request.description?.let { entity.description = it; changed += "description" }
        request.role?.let {
            if (!roleRepo.existsByName(it)) throw RoleNotFoundException(it)
            entity.role = it; changed += "role"
        }
        request.effect?.let { entity.effect = it; changed += "effect" }
        request.action?.let { entity.action = it; changed += "action" }
        request.resourcePattern?.let { entity.resourcePattern = it; changed += "resourcePattern" }
        request.conditions?.let { entity.conditions = it; changed += "conditions" }
        request.priority?.let { entity.priority = it; changed += "priority" }
        request.enabled?.let { entity.enabled = it; changed += "enabled" }

        policyRepo.save(entity)
        if (changed.isNotEmpty()) events.policyUpdated(entity.id, entity.name, changed)
        return entity.toResponse()
    }

    fun delete(id: UUID) {
        val entity = findOrThrow(id)
        policyRepo.delete(entity)
        events.policyDeleted(entity.id, entity.name)
    }

    private fun findOrThrow(id: UUID): PolicyEntity =
        policyRepo.findById(id).orElseThrow { PolicyNotFoundException(id) }
}
