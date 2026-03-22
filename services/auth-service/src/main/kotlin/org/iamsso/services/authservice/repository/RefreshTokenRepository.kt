package org.iamsso.services.authservice.repository

import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    fun revokeAllByUserId(userId: UUID): Int

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.clientId = :clientId AND r.revoked = false")
    fun revokeAllByClientId(clientId: String): Int

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.sessionId = :sessionId AND r.revoked = false")
    fun revokeAllBySessionId(sessionId: UUID): Int
}