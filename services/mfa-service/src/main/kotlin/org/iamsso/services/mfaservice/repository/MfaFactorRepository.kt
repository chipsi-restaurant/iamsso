package org.iamsso.services.mfaservice.repository

import org.iamsso.services.mfaservice.entity.MfaFactorEntity
import org.iamsso.services.mfaservice.entity.MfaFactorStatus
import org.iamsso.services.mfaservice.entity.MfaFactorType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MfaFactorRepository : JpaRepository<MfaFactorEntity, UUID> {
    fun findAllByUserId(userId: UUID): List<MfaFactorEntity>
    fun findByUserIdAndFactorType(userId: UUID, factorType: MfaFactorType): MfaFactorEntity?
    fun findByUserIdAndFactorTypeAndStatus(userId: UUID, factorType: MfaFactorType, status: MfaFactorStatus): MfaFactorEntity?
}
