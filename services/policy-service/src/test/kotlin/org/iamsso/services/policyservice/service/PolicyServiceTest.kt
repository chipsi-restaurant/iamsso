package org.iamsso.services.policyservice.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.services.policyservice.entity.PolicyEffect
import org.iamsso.services.policyservice.entity.PolicyEntity
import org.iamsso.services.policyservice.exception.PolicyAlreadyExistsException
import org.iamsso.services.policyservice.exception.PolicyNotFoundException
import org.iamsso.services.policyservice.exception.RoleNotFoundException
import org.iamsso.services.policyservice.mapper.CreatePolicyRequest
import org.iamsso.services.policyservice.mapper.UpdatePolicyRequest
import org.iamsso.services.policyservice.repository.PolicyRepository
import org.iamsso.services.policyservice.repository.RoleRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyServiceTest {

    @Mock lateinit var policyRepo: PolicyRepository
    @Mock lateinit var roleRepo: RoleRepository
    @Mock lateinit var events: EventPublisher

    private lateinit var service: PolicyService

    @BeforeEach
    fun setUp() {
        service = PolicyService(policyRepo, roleRepo, events)
    }

    @Test
    fun `create policy returns PolicyResponse and publishes event`() {
        whenever(policyRepo.existsByName("test")).thenReturn(false)
        whenever(roleRepo.existsByName("admin")).thenReturn(true)
        val result = service.create(CreatePolicyRequest(
            name = "test", role = "admin", effect = PolicyEffect.ALLOW,
            action = "READ", resourcePattern = "orders/*",
        ))
        assertThat(result.name).isEqualTo("test")
        verify(policyRepo).save(any())
        verify(events).policyCreated(any(), any(), any(), any(), any())
    }

    @Test
    fun `create duplicate policy name throws PolicyAlreadyExistsException`() {
        whenever(policyRepo.existsByName("dup")).thenReturn(true)
        assertThatThrownBy {
            service.create(CreatePolicyRequest(
                name = "dup", role = "admin", effect = PolicyEffect.ALLOW,
                action = "READ", resourcePattern = "*",
            ))
        }.isInstanceOf(PolicyAlreadyExistsException::class.java)
    }

    @Test
    fun `create policy with nonexistent role throws RoleNotFoundException`() {
        whenever(policyRepo.existsByName("test")).thenReturn(false)
        whenever(roleRepo.existsByName("ghost")).thenReturn(false)
        assertThatThrownBy {
            service.create(CreatePolicyRequest(
                name = "test", role = "ghost", effect = PolicyEffect.ALLOW,
                action = "READ", resourcePattern = "*",
            ))
        }.isInstanceOf(RoleNotFoundException::class.java)
    }

    @Test
    fun `update policy changes fields and publishes event`() {
        val id = UUID.randomUUID()
        val entity = PolicyEntity(id = id, name = "old", role = "admin",
            effect = PolicyEffect.ALLOW, action = "READ", resourcePattern = "*")
        whenever(policyRepo.findById(id)).thenReturn(Optional.of(entity))
        whenever(policyRepo.existsByName("new")).thenReturn(false)
        val result = service.update(id, UpdatePolicyRequest(name = "new", priority = 10))
        assertThat(result.name).isEqualTo("new")
        assertThat(result.priority).isEqualTo(10)
        verify(events).policyUpdated(any(), any(), any())
    }

    @Test
    fun `delete policy publishes event`() {
        val id = UUID.randomUUID()
        val entity = PolicyEntity(id = id, name = "to-delete", role = "admin",
            effect = PolicyEffect.ALLOW, action = "READ", resourcePattern = "*")
        whenever(policyRepo.findById(id)).thenReturn(Optional.of(entity))
        service.delete(id)
        verify(policyRepo).delete(entity)
        verify(events).policyDeleted(id, "to-delete")
    }

    @Test
    fun `getById not found throws PolicyNotFoundException`() {
        val id = UUID.randomUUID()
        whenever(policyRepo.findById(id)).thenReturn(Optional.empty())
        assertThatThrownBy { service.getById(id) }
            .isInstanceOf(PolicyNotFoundException::class.java)
    }
}
