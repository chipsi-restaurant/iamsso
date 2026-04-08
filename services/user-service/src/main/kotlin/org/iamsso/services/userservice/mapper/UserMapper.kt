package org.iamsso.services.userservice.mapper

import org.iamsso.contracts.user.model.CredentialsVerificationResponse
import org.iamsso.contracts.user.model.MfaEnrollmentResponse
import org.iamsso.contracts.user.model.MfaFactorResponse
import org.iamsso.contracts.user.model.MfaFactorStatus
import org.iamsso.contracts.user.model.MfaFactorType
import org.iamsso.contracts.user.model.MfaStatusResponse
import org.iamsso.contracts.user.model.PagedUserResponse
import org.iamsso.contracts.user.model.UserProfileResponse
import org.iamsso.contracts.user.model.UserResponse
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.entity.MfaFactorEntity
import org.iamsso.services.userservice.entity.MfaFactorStatus as EntityMfaFactorStatus
import org.iamsso.services.userservice.entity.MfaFactorType as EntityMfaFactorType
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
        mfaEnabled = e.mfaEnabled,
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

    fun toMfaFactorResponse(e: MfaFactorEntity) = MfaFactorResponse(
        id = e.id,
        factorType = MfaFactorType.valueOf(e.factorType.name),
        status = MfaFactorStatus.valueOf(e.status.name),
        displayName = e.displayName,
        createdAt = e.createdAt,
    )

    fun toMfaEnrollmentResponse(factor: MfaFactorEntity, secret: String?, provisioningUri: String?) =
        MfaEnrollmentResponse(
            factorId = factor.id,
            factorType = MfaFactorType.valueOf(factor.factorType.name),
            status = MfaFactorStatus.valueOf(factor.status.name),
            secret = secret,
            provisioningUri = provisioningUri,
        )

    fun toMfaStatusResponse(userId: UUID, factors: List<MfaFactorEntity>): MfaStatusResponse {
        val active = factors.filter { it.status == EntityMfaFactorStatus.ACTIVE }
        return MfaStatusResponse(
            userId = userId,
            mfaEnabled = active.isNotEmpty(),
            activeFactors = active.map { it.factorType.toContract() },
        )
    }

    fun EntityMfaFactorType.toContract(): MfaFactorType {
        return when (this) {
            EntityMfaFactorType.TOTP -> MfaFactorType.TOTP
            EntityMfaFactorType.EMAIL_OTP -> MfaFactorType.EMAIL_OTP
        }
    }

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