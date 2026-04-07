package org.iamsso.services.userservice.repository

import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {

    fun findByEmail(email: String): UserEntity?
    fun findByUsername(username: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun existsByUsername(username: String): Boolean

    @Query("""
        SELECT u FROM UserEntity u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:query IS NULL
               OR LOWER(CAST(u.email AS String)) LIKE LOWER(CONCAT('%', CAST(:query AS String), '%'))
               OR LOWER(CAST(u.username AS String)) LIKE LOWER(CONCAT('%', CAST(:query AS String), '%'))
               OR LOWER(CAST(u.displayName AS String)) LIKE LOWER(CONCAT('%', CAST(:query AS String), '%')))
    """)
    fun search(
        @Param("status") status: UserStatus?,
        @Param("query") query: String?,
        pageable: Pageable,
    ): Page<UserEntity>
}