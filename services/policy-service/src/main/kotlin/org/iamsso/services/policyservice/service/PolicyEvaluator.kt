package org.iamsso.services.policyservice.service

import org.iamsso.services.policyservice.entity.PolicyCondition
import org.iamsso.services.policyservice.entity.PolicyEffect
import org.iamsso.services.policyservice.entity.PolicyEntity
import org.iamsso.services.policyservice.mapper.EvaluateRequest
import org.iamsso.services.policyservice.mapper.EvaluateResponse
import org.iamsso.services.policyservice.repository.PolicyRepository
import org.springframework.stereotype.Component

@Component
class PolicyEvaluator(private val policyRepo: PolicyRepository) {

    fun evaluate(request: EvaluateRequest): EvaluateResponse {
        val candidates = policyRepo.findAllByRoleAndEnabledTrue(request.subject.role)

        val matching = candidates
            .filter { matchesAction(it.action, request.action) }
            .filter { matchesResource(it.resourcePattern, request.resource) }
            .filter { matchesConditions(it.conditions, request) }

        if (matching.isEmpty()) {
            return EvaluateResponse(allowed = false, reason = "No matching policy found, deny by default")
        }

        val best = matching.sortedWith(
            compareByDescending<PolicyEntity> { it.priority }
                .thenBy { if (it.effect == PolicyEffect.DENY) 0 else 1 }
        ).first()

        return EvaluateResponse(
            allowed = best.effect == PolicyEffect.ALLOW,
            policyId = best.id,
            reason = "Policy '${best.name}' ${if (best.effect == PolicyEffect.ALLOW) "allowed" else "denied"}",
        )
    }

    private fun matchesAction(pattern: String, action: String): Boolean =
        pattern == "*" || pattern.equals(action, ignoreCase = true)

    private fun matchesResource(pattern: String, resource: String): Boolean {
        if (pattern == "*") return true
        if (pattern == resource) return true
        if (pattern.endsWith("/*")) {
            val prefix = pattern.dropLast(2)
            return resource.startsWith("$prefix/") || resource == prefix
        }
        return false
    }

    private fun matchesConditions(conditions: List<PolicyCondition>?, request: EvaluateRequest): Boolean {
        if (conditions.isNullOrEmpty()) return true
        return conditions.all { matchCondition(it, request) }
    }

    private fun matchCondition(condition: PolicyCondition, request: EvaluateRequest): Boolean {
        val actualValue = resolveField(condition.field, request) ?: return false
        val expectedValue = resolveValue(condition.value, request)

        return when (condition.operator) {
            "==" -> actualValue == expectedValue
            "!=" -> actualValue != expectedValue
            "in" -> actualValue in expectedValue.split(",").map { it.trim() }
            "not_in" -> actualValue !in expectedValue.split(",").map { it.trim() }
            else -> false
        }
    }

    private fun resolveField(field: String, request: EvaluateRequest): String? {
        if (field.startsWith("subject.")) {
            return when (field) {
                "subject.user_id" -> request.subject.userId
                "subject.role" -> request.subject.role
                "subject.session_id" -> request.subject.sessionId
                else -> null
            }
        }
        return request.context?.get(field)
    }

    private fun resolveValue(value: String, request: EvaluateRequest): String {
        if (value.startsWith("\$subject.")) {
            val field = value.removePrefix("\$")
            return resolveField(field, request) ?: value
        }
        return value
    }
}
