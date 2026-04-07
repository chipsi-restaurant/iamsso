package org.iamsso.services.userservice.service

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.iamsso.contracts.user.model.EnrollMfaRequest
import org.iamsso.contracts.user.model.MfaEnrollmentResponse
import org.iamsso.contracts.user.model.MfaFactorResponse
import org.iamsso.contracts.user.model.MfaStatusResponse
import org.iamsso.services.userservice.entity.MfaFactorEntity
import org.iamsso.services.userservice.entity.MfaFactorStatus
import org.iamsso.services.userservice.entity.MfaFactorType
import org.iamsso.services.userservice.exception.InvalidMfaCodeException
import org.iamsso.services.userservice.exception.MfaFactorAlreadyExistsException
import org.iamsso.services.userservice.exception.MfaFactorNotFoundException
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.mapper.UserMapper
import org.iamsso.services.userservice.repository.MfaFactorRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class MfaService(
    private val factorRepo: MfaFactorRepository,
    private val userRepo: UserRepository,
    private val events: EventPublisher,
) {
    private val secretGen = DefaultSecretGenerator()
    private val codeVerifier = DefaultCodeVerifier(DefaultCodeGenerator(), SystemTimeProvider())

    @Transactional(readOnly = true)
    fun listFactors(userId: UUID): List<MfaFactorResponse> {
        if (!userRepo.existsById(userId)) throw UserNotFoundException(userId)
        return factorRepo.findAllByUserId(userId).map { UserMapper.toMfaFactorResponse(it) }
    }

    @Transactional
    fun enroll(userId: UUID, request: EnrollMfaRequest): MfaEnrollmentResponse {
        val user = userRepo.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val type = MfaFactorType.valueOf(request.factorType.name)

        factorRepo.findByUserIdAndFactorType(userId, type)?.let { throw MfaFactorAlreadyExistsException(type.name) }

        val secret = if (type == MfaFactorType.TOTP) secretGen.generate() else null
        val factor = MfaFactorEntity(user = user, factorType = type, displayName = request.displayName, secret = secret)
        factorRepo.save(factor)

        if (type == MfaFactorType.EMAIL_OTP && user.email != null) {
            val code = (100_000..999_999).random().toString()
            factor.secret = code
            events.sendEmailOtp(userId, user.email!!, code, Instant.now().plusSeconds(300))
        }

        val uri = if (type == MfaFactorType.TOTP && secret != null) {
            val account = user.email ?: user.username ?: userId.toString()
            "otpauth://totp/IAM:$account?secret=$secret&issuer=IAM"
        } else null

        return UserMapper.toMfaEnrollmentResponse(factor, secret, uri)
    }

    @Transactional
    fun confirm(userId: UUID, factorId: UUID, code: String): MfaFactorResponse {
        val factor = findFactor(userId, factorId)
        val valid = when (factor.factorType) {
            MfaFactorType.TOTP -> codeVerifier.isValidCode(factor.secret, code)
            MfaFactorType.EMAIL_OTP -> factor.secret == code
        }
        if (!valid) throw InvalidMfaCodeException()

        factor.status = MfaFactorStatus.ACTIVE
        if (factor.factorType == MfaFactorType.EMAIL_OTP) factor.secret = null
        events.mfaFactorEnrolled(userId, factorId, factor.factorType.name)
        return UserMapper.toMfaFactorResponse(factor)
    }

    @Transactional
    fun remove(userId: UUID, factorId: UUID) {
        val factor = findFactor(userId, factorId)
        factorRepo.delete(factor)
        events.mfaFactorRemoved(userId, factorId, factor.factorType.name)
    }

    @Transactional(readOnly = true)
    fun getStatus(userId: UUID): MfaStatusResponse =
        UserMapper.toMfaStatusResponse(userId, factorRepo.findAllByUserId(userId))

    private fun findFactor(userId: UUID, factorId: UUID): MfaFactorEntity {
        val factor = factorRepo.findById(factorId).orElseThrow { MfaFactorNotFoundException(factorId) }
        if (factor.user?.id != userId) throw MfaFactorNotFoundException(factorId)
        return factor
    }
}