package org.iamsso.services.userservice.service

import org.iamsso.contracts.user.model.CredentialsVerificationResponse
import org.iamsso.services.userservice.config.AppProperties
import org.iamsso.services.userservice.entity.UserStatus
import org.iamsso.services.userservice.exception.InvalidCredentialsException
import org.iamsso.services.userservice.exception.ServiceException
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.mapper.UserMapper
import org.iamsso.services.userservice.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class CredentialsService(
    private val userRepo: UserRepository,
    private val encoder: PasswordEncoder,
    private val events: EventPublisher,
    private val props: AppProperties,
) {

    @Transactional
    fun verify(userId: UUID, rawPassword: String, ip: String?, userAgent: String?): CredentialsVerificationResponse {
        val user = userRepo.findById(userId).orElseThrow { UserNotFoundException(userId) }

        user.lockedUntil?.let {
            if (it.isAfter(Instant.now())) return UserMapper.toCredentialsResponse(user,
                valid = false,
                mfaRequired = false
            )
            user.lockedUntil = null; user.failedAttempts = 0
            if (user.status == UserStatus.LOCKED) user.status = UserStatus.ACTIVE
        }

        val valid = encoder.matches(rawPassword, user.passwordHash)

        if (!valid) {
            user.failedAttempts++
            if (user.failedAttempts >= props.credentials.maxFailedAttempts) {
                user.status = UserStatus.LOCKED
                user.lockedUntil = Instant.now().plus(props.credentials.lockDurationMinutes, ChronoUnit.MINUTES)
            }
            events.credentialsVerifyFailed(user.id, user.failedAttempts, ip, userAgent)
        } else {
            user.failedAttempts = 0; user.lockedUntil = null
        }

        return UserMapper.toCredentialsResponse(user, valid, mfaRequired = false)
    }

    @Transactional
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepo.findById(userId).orElseThrow { UserNotFoundException(userId) }
        if (!encoder.matches(currentPassword, user.passwordHash)) throw InvalidCredentialsException()
        user.passwordHash =
            encoder.encode(newPassword) ?: throw ServiceException("PASSWORD_HASHING_FAILED", "Failed to hash password")
        events.credentialsPasswordChanged(user.id)
    }

    @Transactional
    fun resetPassword(userId: UUID, newPassword: String, initiatedBy: String) {
        val user = userRepo.findById(userId).orElseThrow { UserNotFoundException(userId) }
        user.passwordHash =
            encoder.encode(newPassword) ?: throw ServiceException("PASSWORD_HASHING_FAILED", "Failed to hash password")
        user.failedAttempts = 0; user.lockedUntil = null
        if (user.status == UserStatus.LOCKED) user.status = UserStatus.ACTIVE
        events.credentialsPasswordReset(user.id, initiatedBy)
    }
}