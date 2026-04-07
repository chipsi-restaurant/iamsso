package org.iamsso.services.policyservice.mapper

import org.iamsso.services.policyservice.entity.PolicyCondition
import org.iamsso.services.policyservice.entity.PolicyEffect
import org.iamsso.services.policyservice.entity.PolicyEntity
import org.iamsso.services.policyservice.entity.RoleEntity
import java.time.Instant

data class RoleResponse(
    val id: java.util.UUID,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreateRoleRequest(
    val name: String,
    val description: String? = null,
)

data class PolicyResponse(
    val id: java.util.UUID,
    val name: String,
    val description: String?,
    val role: String,
    val effect: PolicyEffect,
    val action: String,
    val resourcePattern: String,
    val conditions: List<PolicyCondition>?,
    val priority: Int,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreatePolicyRequest(
    val name: String,
    val description: String? = null,
    val role: String,
    val effect: PolicyEffect,
    val action: String,
    val resourcePattern: String,
    val conditions: List<PolicyCondition>? = null,
    val priority: Int? = null,
    val enabled: Boolean? = null,
) {
    fun resolvedPriority(): Int = priority ?: 0
    fun resolvedEnabled(): Boolean = enabled ?: true
}

data class UpdatePolicyRequest(
    val name: String? = null,
    val description: String? = null,
    val role: String? = null,
    val effect: PolicyEffect? = null,
    val action: String? = null,
    val resourcePattern: String? = null,
    val conditions: List<PolicyCondition>? = null,
    val priority: Int? = null,
    val enabled: Boolean? = null,
)

data class EvaluateRequest(
    val subject: SubjectInfo,
    val action: String,
    val resource: String,
    val context: Map<String, String>? = null,
)

data class SubjectInfo(
    val userId: String,
    val role: String,
    val sessionId: String? = null,
)

data class EvaluateResponse(
    val allowed: Boolean,
    val policyId: java.util.UUID? = null,
    val reason: String,
)

fun RoleEntity.toResponse() = RoleResponse(id, name, description, createdAt, updatedAt)

fun PolicyEntity.toResponse() = PolicyResponse(
    id, name, description, role, effect, action, resourcePattern,
    conditions, priority, enabled, createdAt, updatedAt,
)
