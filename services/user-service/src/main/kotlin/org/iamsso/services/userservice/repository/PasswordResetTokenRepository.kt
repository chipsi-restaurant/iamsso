package org.iamsso.services.userservice.repository

import org.iamsso.services.userservice.entity.PasswordResetTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenEntity, UUID> {
    fun findByToken(token: String): PasswordResetTokenEntity?
    fun deleteAllByUserId(userId: UUID)
}
