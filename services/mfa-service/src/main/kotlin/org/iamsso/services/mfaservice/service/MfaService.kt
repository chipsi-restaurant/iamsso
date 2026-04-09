package org.iamsso.services.mfaservice.service

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.iamsso.contracts.mfa.model.EnrollMfaFactorRequest
import org.iamsso.contracts.mfa.model.MfaEnrollmentResponse
import org.iamsso.contracts.mfa.model.MfaFactorResponse
import org.iamsso.contracts.mfa.model.MfaStatusResponse
import org.iamsso.contracts.mfa.model.VerifyMfaRequest
import org.iamsso.contracts.mfa.model.VerifyMfaResponse
import org.iamsso.services.mfaservice.config.AppProperties
import org.iamsso.services.mfaservice.entity.MfaFactorEntity
import org.iamsso.services.mfaservice.entity.MfaFactorStatus
import org.iamsso.services.mfaservice.entity.MfaFactorType
import org.iamsso.services.mfaservice.exception.InvalidMfaCodeException
import org.iamsso.services.mfaservice.exception.MfaFactorAlreadyExistsException
import org.iamsso.services.mfaservice.exception.MfaFactorNotFoundException
import org.iamsso.services.mfaservice.exception.NoActiveFactorException
import org.iamsso.services.mfaservice.repository.MfaFactorRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class MfaService(
    private val factorRepo: MfaFactorRepository,
    private val events: EventPublisher,
    private val props: AppProperties,
) {
    private val secretGen = DefaultSecretGenerator()
    private val codeVerifier = DefaultCodeVerifier(DefaultCodeGenerator(), SystemTimeProvider())

    @Transactional(readOnly = true)
    fun listFactors(userId: UUID): List<MfaFactorResponse> =
        factorRepo.findAllByUserId(userId).map { it.toResponse() }

    @Transactional
    fun enroll(userId: UUID, request: EnrollMfaFactorRequest, email: String?): MfaEnrollmentResponse {
        val type = MfaFactorType.valueOf(request.factorType!!.name)

        factorRepo.findByUserIdAndFactorType(userId, type)?.let {
            throw MfaFactorAlreadyExistsException(type.name)
        }

        val secret: String
        var provisioningUri: String? = null

        when (type) {
            MfaFactorType.TOTP -> {
                secret = secretGen.generate()
                val account = email ?: userId.toString()
                provisioningUri = "otpauth://totp/${props.totp.issuer}:$account?secret=$secret&issuer=${props.totp.issuer}"
            }
            MfaFactorType.EMAIL_OTP -> {
                secret = generateOtpCode()
                if (email != null) {
                    events.sendEmailOtp(userId, email, secret, Instant.now().plusSeconds(props.emailOtp.ttlSeconds))
                }
            }
        }

        val factor = MfaFactorEntity(
            userId = userId,
            factorType = type,
            displayName = request.displayName,
            secret = secret,
        )
        factorRepo.save(factor)

        return MfaEnrollmentResponse(
            factorId = factor.id,
            factorType = factor.factorType.toContract(),
            status = factor.status.toContract(),
            secret = if (type == MfaFactorType.TOTP) secret else null,
            provisioningUri = provisioningUri,
        )
    }

    private fun generateOtpCode(): String = (100_000..999_999).random().toString()

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
        return factor.toResponse()
    }

    @Transactional
    fun remove(userId: UUID, factorId: UUID) {
        val factor = findFactor(userId, factorId)
        factorRepo.delete(factor)
        events.mfaFactorRemoved(userId, factorId, factor.factorType.name)
    }

    @Transactional
    fun verify(userId: UUID, request: VerifyMfaRequest): VerifyMfaResponse {
        val requestedType = request.factorType?.let { MfaFactorType.valueOf(it.name) }

        if (requestedType != null) {
            val factor = factorRepo.findByUserIdAndFactorTypeAndStatus(userId, requestedType, MfaFactorStatus.ACTIVE)
                ?: throw NoActiveFactorException()
            val valid = verifyCode(factor, request.code!!)
            return VerifyMfaResponse(valid = valid)
        }

        // No specific type — try all active factors, TOTP first
        val activeFactors = factorRepo.findAllByUserId(userId)
            .filter { it.status == MfaFactorStatus.ACTIVE }
            .sortedBy { if (it.factorType == MfaFactorType.TOTP) 0 else 1 }

        if (activeFactors.isEmpty()) throw NoActiveFactorException()

        val valid = activeFactors.any { verifyCode(it, request.code!!) }
        return VerifyMfaResponse(valid = valid)
    }

    private fun verifyCode(factor: MfaFactorEntity, code: String): Boolean = when (factor.factorType) {
        MfaFactorType.TOTP -> codeVerifier.isValidCode(factor.secret, code)
        MfaFactorType.EMAIL_OTP -> factor.secret == code
    }

    @Transactional(readOnly = true)
    fun getStatus(userId: UUID): MfaStatusResponse {
        val factors = factorRepo.findAllByUserId(userId)
        val active = factors.filter { it.status == MfaFactorStatus.ACTIVE }
        return MfaStatusResponse(
            userId = userId,
            mfaEnabled = active.isNotEmpty(),
            activeFactors = active.map { it.factorType.toContract() },
        )
    }

    @Transactional
    fun sendOtp(userId: UUID, email: String) {
        val factor = factorRepo.findByUserIdAndFactorTypeAndStatus(userId, MfaFactorType.EMAIL_OTP, MfaFactorStatus.ACTIVE)
            ?: throw NoActiveFactorException()
        val code = generateOtpCode()
        factor.secret = code
        events.sendEmailOtp(userId, email, code, Instant.now().plusSeconds(props.emailOtp.ttlSeconds))
    }

    private fun findFactor(userId: UUID, factorId: UUID): MfaFactorEntity {
        val factor = factorRepo.findById(factorId).orElseThrow { MfaFactorNotFoundException(factorId) }
        if (factor.userId != userId) throw MfaFactorNotFoundException(factorId)
        return factor
    }

    private fun MfaFactorEntity.toResponse() = MfaFactorResponse(
        id = id,
        factorType = factorType.toContract(),
        status = status.toContract(),
        displayName = displayName,
        createdAt = createdAt,
    )

    private fun MfaFactorType.toContract() =
        org.iamsso.contracts.mfa.model.MfaFactorType.valueOf(name)

    private fun MfaFactorStatus.toContract() =
        org.iamsso.contracts.mfa.model.MfaFactorStatus.valueOf(name)
}
