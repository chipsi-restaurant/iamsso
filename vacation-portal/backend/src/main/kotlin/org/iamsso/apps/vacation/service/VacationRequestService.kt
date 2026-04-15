package org.iamsso.apps.vacation.service

import org.iamsso.apps.vacation.controller.CreateRequestBody
import org.iamsso.apps.vacation.entity.VacationRequestEntity
import org.iamsso.apps.vacation.entity.VacationRequestStatus
import org.iamsso.apps.vacation.exception.ForbiddenException
import org.iamsso.apps.vacation.exception.InvalidStateException
import org.iamsso.apps.vacation.exception.NotFoundException
import org.iamsso.apps.vacation.repository.VacationRequestRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class VacationRequestService(private val repo: VacationRequestRepository) {

    @Transactional
    fun create(userId: UUID, body: CreateRequestBody): VacationRequestEntity {
        require(body.type != null) { "type is required" }
        require(body.startDate != null) { "startDate is required" }
        require(body.endDate != null) { "endDate is required" }
        require(!body.reason.isNullOrBlank()) { "reason is required" }
        require(!body.endDate.isBefore(body.startDate)) { "endDate must be >= startDate" }

        val entity = VacationRequestEntity(
            userId = userId,
            type = body.type,
            startDate = body.startDate,
            endDate = body.endDate,
            reason = body.reason,
        )
        return repo.save(entity)
    }

    @Transactional(readOnly = true)
    fun getById(id: UUID, currentUserId: UUID, isManager: Boolean): VacationRequestEntity {
        val entity = repo.findById(id).orElseThrow { NotFoundException("vacation-request", id) }
        if (!isManager && entity.userId != currentUserId) throw ForbiddenException("Not your request")
        return entity
    }

    @Transactional(readOnly = true)
    fun findMyRequests(userId: UUID, page: Int, size: Int): Page<VacationRequestEntity> =
        repo.findByUserId(userId, pageable(page, size))

    @Transactional(readOnly = true)
    fun findAll(status: VacationRequestStatus?, page: Int, size: Int): Page<VacationRequestEntity> =
        if (status != null) repo.findByStatus(status, pageable(page, size))
        else repo.findAll(pageable(page, size))

    @Transactional
    fun approve(id: UUID, reviewerId: UUID, comment: String?): VacationRequestEntity {
        val entity = repo.findById(id).orElseThrow { NotFoundException("vacation-request", id) }
        if (entity.status.isTerminal) throw InvalidStateException("Cannot approve request in status ${entity.status}")
        entity.status = VacationRequestStatus.APPROVED
        entity.reviewerId = reviewerId
        entity.reviewComment = comment
        entity.reviewedAt = Instant.now()
        return entity
    }

    @Transactional
    fun reject(id: UUID, reviewerId: UUID, comment: String?): VacationRequestEntity {
        val entity = repo.findById(id).orElseThrow { NotFoundException("vacation-request", id) }
        if (entity.status.isTerminal) throw InvalidStateException("Cannot reject request in status ${entity.status}")
        entity.status = VacationRequestStatus.REJECTED
        entity.reviewerId = reviewerId
        entity.reviewComment = comment
        entity.reviewedAt = Instant.now()
        return entity
    }

    @Transactional
    fun cancel(id: UUID, currentUserId: UUID): VacationRequestEntity {
        val entity = repo.findById(id).orElseThrow { NotFoundException("vacation-request", id) }
        if (entity.userId != currentUserId) throw ForbiddenException("Not your request")
        if (entity.status != VacationRequestStatus.PENDING)
            throw InvalidStateException("Can only cancel requests in PENDING status")
        entity.status = VacationRequestStatus.CANCELLED
        return entity
    }

    private fun pageable(page: Int, size: Int) =
        PageRequest.of(page, size.coerceAtMost(200), Sort.by(Sort.Direction.DESC, "createdAt"))
}
