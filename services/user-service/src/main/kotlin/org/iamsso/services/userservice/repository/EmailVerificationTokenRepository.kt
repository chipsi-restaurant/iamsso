package org.iamsso.services.userservice.repository

import org.iamsso.services.userservice.entity.EmailVerificationTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationTokenEntity, UUID> {
    fun findByToken(token: String): EmailVerificationTokenEntity?
    fun deleteAllByUserId(userId: UUID)
    fun findFirstByUserIdOrderByCreatedAtDesc(userId: UUID): EmailVerificationTokenEntity?
}