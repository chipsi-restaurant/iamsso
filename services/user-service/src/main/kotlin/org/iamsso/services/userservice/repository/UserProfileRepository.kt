package org.iamsso.services.userservice.repository

import org.iamsso.services.userservice.entity.UserProfileEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfileEntity, UUID>