package org.iamsso.apps.vacation.repository

import org.iamsso.apps.vacation.entity.VacationRequestEntity
import org.iamsso.apps.vacation.entity.VacationRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VacationRequestRepository : JpaRepository<VacationRequestEntity, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<VacationRequestEntity>
    fun findByStatus(status: VacationRequestStatus, pageable: Pageable): Page<VacationRequestEntity>
}
