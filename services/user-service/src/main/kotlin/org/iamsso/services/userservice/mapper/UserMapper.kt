package org.iamsso.services.userservice.mapper

import org.iamsso.contracts.user.model.CredentialsVerificationResponse
import org.iamsso.contracts.user.model.PagedUserResponse
import org.iamsso.contracts.user.model.UserProfileResponse
import org.iamsso.contracts.user.model.UserResponse
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.entity.UserStatus as EntityUserStatus
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserProfileEntity
import org.springframework.data.domain.Page
import java.util.UUID

object UserMapper {

    fun toResponse(e: UserEntity) = UserResponse(
        id = e.id,
        email = e.email,
        username = e.username,
        displayName = e.displayName,
        status = UserStatus.valueOf(e.status.name),
        emailVerified = e.emailVerified,
        mfaEnabled = false,
        locale = e.locale,
        role = e.role,
        createdAt = e.createdAt,
        updatedAt = e.updatedAt,
    )

    fun toPagedResponse(page: Page<UserEntity>) = PagedUserResponse(
        content = page.content.map { toResponse(it) },
        page = page.number,
        propertySize = page.size,
        totalElements = page.totalElements,
        totalPages = page.totalPages,
    )

    fun toCredentialsResponse(user: UserEntity, valid: Boolean, mfaRequired: Boolean) =
        CredentialsVerificationResponse(
            valid = valid,
            userId = user.id,
            status = UserStatus.valueOf(user.status.name),
            mfaRequired = mfaRequired,
            failedAttempts = user.failedAttempts,
            lockedUntil = user.lockedUntil,
        )

    fun toProfileResponse(user: UserEntity, profile: UserProfileEntity?) = UserProfileResponse(
        userId = user.id,
        displayName = user.displayName,
        firstName = profile?.firstName,
        lastName = profile?.lastName,
        avatarUrl = profile?.avatarUrl,
        timezone = profile?.timezone,
        locale = user.locale,
        updatedAt = profile?.updatedAt ?: user.updatedAt,
    )

    fun UserStatus.toEntity(): EntityUserStatus {
        return when (this) {
            UserStatus.PENDING_VERIFICATION -> EntityUserStatus.PENDING_VERIFICATION
            UserStatus.ACTIVE -> EntityUserStatus.ACTIVE
            UserStatus.LOCKED -> EntityUserStatus.LOCKED
            UserStatus.SUSPENDED -> EntityUserStatus.SUSPENDED
            UserStatus.DELETED -> EntityUserStatus.DELETED
        }
    }

    fun UserStatus?.toEntityOrNull(): EntityUserStatus? = this?.toEntity()
}
