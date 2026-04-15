package org.iamsso.apps.vacation.service

import org.iamsso.apps.vacation.controller.CreateRequestBody
import org.iamsso.apps.vacation.entity.VacationRequestEntity
import org.iamsso.apps.vacation.entity.VacationRequestStatus
import org.iamsso.apps.vacation.entity.VacationRequestType
import org.iamsso.apps.vacation.exception.ForbiddenException
import org.iamsso.apps.vacation.exception.InvalidStateException
import org.iamsso.apps.vacation.exception.NotFoundException
import org.iamsso.apps.vacation.repository.VacationRequestRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class VacationRequestServiceTest {

    @Mock lateinit var repo: VacationRequestRepository

    private lateinit var service: VacationRequestService

    @BeforeEach
    fun setUp() {
        service = VacationRequestService(repo)
    }

    private fun sampleEntity(
        userId: UUID = UUID.randomUUID(),
        status: VacationRequestStatus = VacationRequestStatus.PENDING,
    ) = VacationRequestEntity(
        userId = userId,
        type = VacationRequestType.VACATION,
        startDate = LocalDate.of(2026, 5, 1),
        endDate = LocalDate.of(2026, 5, 10),
        reason = "summer holiday",
        status = status,
    )

    @Test
    fun `create saves valid request`() {
        val userId = UUID.randomUUID()
        val body = CreateRequestBody(
            type = VacationRequestType.VACATION,
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 10),
            reason = "holiday",
        )
        whenever(repo.save(any<VacationRequestEntity>())).thenAnswer { it.arguments[0] }

        val result = service.create(userId, body)

        assertEquals(userId, result.userId)
        assertEquals(VacationRequestType.VACATION, result.type)
        assertEquals(VacationRequestStatus.PENDING, result.status)
    }

    @Test
    fun `create rejects end date before start date`() {
        val body = CreateRequestBody(
            type = VacationRequestType.VACATION,
            startDate = LocalDate.of(2026, 5, 10),
            endDate = LocalDate.of(2026, 5, 1),
            reason = "x",
        )
        assertThrows<IllegalArgumentException> { service.create(UUID.randomUUID(), body) }
    }

    @Test
    fun `getById returns for owner`() {
        val userId = UUID.randomUUID()
        val entity = sampleEntity(userId = userId)
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        val result = service.getById(entity.id, userId, isManager = false)
        assertEquals(entity.id, result.id)
    }

    @Test
    fun `getById forbids non-owner non-manager`() {
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        val entity = sampleEntity(userId = owner)
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        assertThrows<ForbiddenException> { service.getById(entity.id, other, isManager = false) }
    }

    @Test
    fun `getById allows manager to access any request`() {
        val entity = sampleEntity()
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        val result = service.getById(entity.id, UUID.randomUUID(), isManager = true)
        assertNotNull(result)
    }

    @Test
    fun `approve transitions PENDING to APPROVED`() {
        val entity = sampleEntity()
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        val reviewer = UUID.randomUUID()
        val result = service.approve(entity.id, reviewer, "ok")

        assertEquals(VacationRequestStatus.APPROVED, result.status)
        assertEquals(reviewer, result.reviewerId)
        assertEquals("ok", result.reviewComment)
        assertNotNull(result.reviewedAt)
    }

    @Test
    fun `approve fails on terminal status`() {
        val entity = sampleEntity(status = VacationRequestStatus.APPROVED)
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        assertThrows<InvalidStateException> { service.approve(entity.id, UUID.randomUUID(), null) }
    }

    @Test
    fun `reject transitions PENDING to REJECTED`() {
        val entity = sampleEntity()
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        val reviewer = UUID.randomUUID()
        val result = service.reject(entity.id, reviewer, "not approved")

        assertEquals(VacationRequestStatus.REJECTED, result.status)
        assertEquals(reviewer, result.reviewerId)
    }

    @Test
    fun `cancel works for owner on PENDING`() {
        val owner = UUID.randomUUID()
        val entity = sampleEntity(userId = owner)
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        val result = service.cancel(entity.id, owner)
        assertEquals(VacationRequestStatus.CANCELLED, result.status)
    }

    @Test
    fun `cancel forbids non-owner`() {
        val entity = sampleEntity()
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        assertThrows<ForbiddenException> { service.cancel(entity.id, UUID.randomUUID()) }
    }

    @Test
    fun `cancel fails for non-pending status`() {
        val owner = UUID.randomUUID()
        val entity = sampleEntity(userId = owner, status = VacationRequestStatus.APPROVED)
        whenever(repo.findById(entity.id)).thenReturn(Optional.of(entity))

        assertThrows<InvalidStateException> { service.cancel(entity.id, owner) }
    }

    @Test
    fun `getById throws NotFound for missing id`() {
        val id = UUID.randomUUID()
        whenever(repo.findById(id)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.getById(id, UUID.randomUUID(), isManager = false) }
    }
}
