package org.iamsso.services.policyservice.service

import org.assertj.core.api.Assertions.assertThat
import org.iamsso.services.policyservice.entity.PolicyCondition
import org.iamsso.services.policyservice.entity.PolicyEffect
import org.iamsso.services.policyservice.entity.PolicyEntity
import org.iamsso.services.policyservice.mapper.EvaluateRequest
import org.iamsso.services.policyservice.mapper.SubjectInfo
import org.iamsso.services.policyservice.repository.PolicyRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyEvaluatorTest {

    @Mock lateinit var policyRepo: PolicyRepository

    private lateinit var evaluator: PolicyEvaluator

    @BeforeEach
    fun setUp() {
        evaluator = PolicyEvaluator(policyRepo)
    }

    private fun policy(
        role: String = "admin",
        effect: PolicyEffect = PolicyEffect.ALLOW,
        action: String = "*",
        resourcePattern: String = "*",
        conditions: List<PolicyCondition>? = null,
        priority: Int = 0,
        name: String = "test-policy",
    ) = PolicyEntity(
        name = name, role = role, effect = effect, action = action,
        resourcePattern = resourcePattern, conditions = conditions, priority = priority,
    )

    private fun request(
        role: String = "admin",
        action: String = "READ",
        resource: String = "orders/123",
        userId: String = UUID.randomUUID().toString(),
        context: Map<String, String>? = null,
    ) = EvaluateRequest(
        subject = SubjectInfo(userId = userId, role = role),
        action = action, resource = resource, context = context,
    )

    @Test
    fun `no matching policies returns deny by default`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("user")).thenReturn(emptyList())
        val result = evaluator.evaluate(request(role = "user"))
        assertThat(result.allowed).isFalse()
        assertThat(result.policyId).isNull()
        assertThat(result.reason).contains("No matching policy")
    }

    @Test
    fun `wildcard action matches any action`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(policy(action = "*")))
        val result = evaluator.evaluate(request(action = "DELETE"))
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `exact action matches`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(policy(action = "READ")))
        val result = evaluator.evaluate(request(action = "READ"))
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `action mismatch does not match`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(policy(action = "READ")))
        val result = evaluator.evaluate(request(action = "DELETE"))
        assertThat(result.allowed).isFalse()
    }

    @Test
    fun `wildcard resource matches any resource`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(policy(resourcePattern = "*")))
        val result = evaluator.evaluate(request(resource = "orders/123"))
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `prefix wildcard matches nested resource`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(policy(resourcePattern = "orders/*")))
        val result = evaluator.evaluate(request(resource = "orders/456"))
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `prefix wildcard does not match different resource`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(policy(resourcePattern = "orders/*")))
        val result = evaluator.evaluate(request(resource = "users/123"))
        assertThat(result.allowed).isFalse()
    }

    @Test
    fun `exact resource matches`() {
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(policy(resourcePattern = "orders/123")))
        val result = evaluator.evaluate(request(resource = "orders/123"))
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `higher priority policy wins`() {
        val denyLow = policy(effect = PolicyEffect.DENY, priority = 0, name = "deny-low")
        val allowHigh = policy(effect = PolicyEffect.ALLOW, priority = 10, name = "allow-high")
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(denyLow, allowHigh))
        val result = evaluator.evaluate(request())
        assertThat(result.allowed).isTrue()
        assertThat(result.reason).contains("allow-high")
    }

    @Test
    fun `DENY wins over ALLOW at equal priority`() {
        val allow = policy(effect = PolicyEffect.ALLOW, priority = 5, name = "allow")
        val deny = policy(effect = PolicyEffect.DENY, priority = 5, name = "deny")
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(allow, deny))
        val result = evaluator.evaluate(request())
        assertThat(result.allowed).isFalse()
        assertThat(result.reason).contains("deny")
    }

    @Test
    fun `condition == matches context value`() {
        val p = policy(conditions = listOf(PolicyCondition("resource.status", "==", "active")))
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(p))
        val result = evaluator.evaluate(request(context = mapOf("resource.status" to "active")))
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `condition != filters out matching context value`() {
        val p = policy(conditions = listOf(PolicyCondition("resource.status", "!=", "completed")))
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(p))
        val result = evaluator.evaluate(request(context = mapOf("resource.status" to "completed")))
        assertThat(result.allowed).isFalse()
    }

    @Test
    fun `condition with subject variable resolves correctly`() {
        val userId = UUID.randomUUID().toString()
        val p = policy(conditions = listOf(PolicyCondition("resource.owner_id", "==", "\$subject.user_id")))
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(p))
        val result = evaluator.evaluate(request(userId = userId, context = mapOf("resource.owner_id" to userId)))
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `condition with subject variable mismatch denies`() {
        val p = policy(conditions = listOf(PolicyCondition("resource.owner_id", "==", "\$subject.user_id")))
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(p))
        val result = evaluator.evaluate(request(
            userId = UUID.randomUUID().toString(),
            context = mapOf("resource.owner_id" to UUID.randomUUID().toString()),
        ))
        assertThat(result.allowed).isFalse()
    }

    @Test
    fun `missing context field fails condition`() {
        val p = policy(conditions = listOf(PolicyCondition("resource.status", "==", "active")))
        whenever(policyRepo.findAllByRoleAndEnabledTrue("admin")).thenReturn(listOf(p))
        val result = evaluator.evaluate(request(context = null))
        assertThat(result.allowed).isFalse()
    }
}
