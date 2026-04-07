package org.iamsso.services.policyservice.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.services.policyservice.entity.RoleEntity
import org.iamsso.services.policyservice.exception.RoleAlreadyExistsException
import org.iamsso.services.policyservice.exception.RoleInUseException
import org.iamsso.services.policyservice.exception.RoleNotFoundException
import org.iamsso.services.policyservice.mapper.CreateRoleRequest
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
class RoleServiceTest {

    @Mock lateinit var roleRepo: RoleRepository
    @Mock lateinit var policyRepo: PolicyRepository

    private lateinit var service: RoleService

    @BeforeEach
    fun setUp() {
        service = RoleService(roleRepo, policyRepo)
    }

    @Test
    fun `create role returns RoleResponse`() {
        whenever(roleRepo.existsByName("viewer")).thenReturn(false)
        val result = service.create(CreateRoleRequest(name = "viewer", description = "Read-only"))
        assertThat(result.name).isEqualTo("viewer")
        verify(roleRepo).save(any())
    }

    @Test
    fun `create duplicate role throws RoleAlreadyExistsException`() {
        whenever(roleRepo.existsByName("admin")).thenReturn(true)
        assertThatThrownBy { service.create(CreateRoleRequest(name = "admin")) }
            .isInstanceOf(RoleAlreadyExistsException::class.java)
    }

    @Test
    fun `delete role succeeds when not in use`() {
        val id = UUID.randomUUID()
        val role = RoleEntity(id = id, name = "temp")
        whenever(roleRepo.findById(id)).thenReturn(Optional.of(role))
        whenever(policyRepo.existsByRole("temp")).thenReturn(false)
        service.delete(id)
        verify(roleRepo).delete(role)
    }

    @Test
    fun `delete role in use throws RoleInUseException`() {
        val id = UUID.randomUUID()
        val role = RoleEntity(id = id, name = "admin")
        whenever(roleRepo.findById(id)).thenReturn(Optional.of(role))
        whenever(policyRepo.existsByRole("admin")).thenReturn(true)
        assertThatThrownBy { service.delete(id) }
            .isInstanceOf(RoleInUseException::class.java)
    }

    @Test
    fun `delete nonexistent role throws RoleNotFoundException`() {
        val id = UUID.randomUUID()
        whenever(roleRepo.findById(id)).thenReturn(Optional.empty())
        assertThatThrownBy { service.delete(id) }
            .isInstanceOf(RoleNotFoundException::class.java)
    }
}
