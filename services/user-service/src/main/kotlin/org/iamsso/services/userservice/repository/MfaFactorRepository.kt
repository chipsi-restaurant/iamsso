package org.iamsso.services.userservice.repository

import org.iamsso.services.userservice.entity.MfaFactorEntity
import org.iamsso.services.userservice.entity.MfaFactorType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MfaFactorRepository : JpaRepository<MfaFactorEntity, UUID> {
    fun findAllByUserId(userId: UUID): List<MfaFactorEntity>
    fun findByUserIdAndFactorType(userId: UUID, factorType: MfaFactorType): MfaFactorEntity?
}