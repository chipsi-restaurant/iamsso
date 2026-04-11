package org.iamsso.services.userservice.service

import org.iamsso.services.userservice.config.AppProperties
import org.iamsso.services.userservice.entity.PasswordResetTokenEntity
import org.iamsso.services.userservice.exception.InvalidPasswordException
import org.iamsso.services.userservice.exception.PasswordResetTokenInvalidException
import org.iamsso.services.userservice.repository.PasswordResetTokenRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PasswordResetService(
    private val userRepo: UserRepository,
    private val tokenRepo: PasswordResetTokenRepository,
    private val credentialsService: CredentialsService,
    private val events: EventPublisher,
    private val sessionClient: SessionServiceClient,
    @Suppress("unused") private val props: AppProperties,
) {
    companion object {
        const val TOKEN_TTL_MINUTES = 15L
    }

    @Transactional
    fun requestReset(email: String) {
        val user = userRepo.findByEmail(email) ?: return // anti-enumeration: silent no-op

        tokenRepo.deleteAllByUserId(user.id)

        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)
        tokenRepo.save(
            PasswordResetTokenEntity(
                userId = user.id,
                token = token,
                expiresAt = expiresAt,
            )
        )

        events.sendPasswordReset(
            userId = user.id,
            email = user.email!!,
            firstName = user.profile?.firstName,
            token = token,
            expiresAt = expiresAt,
        )
    }

    @Transactional(readOnly = true)
    fun validate(token: String) {
        val entity = tokenRepo.findByToken(token) ?: throw PasswordResetTokenInvalidException()
        if (entity.expiresAt.isBefore(Instant.now())) throw PasswordResetTokenInvalidException()
    }

    @Transactional
    fun confirmReset(token: String, newPassword: String) {
        val entity = tokenRepo.findByToken(token) ?: throw PasswordResetTokenInvalidException()
        if (entity.expiresAt.isBefore(Instant.now())) throw PasswordResetTokenInvalidException()

        if (!PasswordPolicy.isValid(newPassword)) throw InvalidPasswordException()

        credentialsService.resetPassword(entity.userId, newPassword, "self-service")
        tokenRepo.delete(entity)

        // Best-effort: session invalidation failure logs WARN but does not fail the flow
        sessionClient.deleteAllForUser(entity.userId)
    }
}
