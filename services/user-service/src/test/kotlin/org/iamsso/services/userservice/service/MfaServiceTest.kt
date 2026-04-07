package org.iamsso.services.userservice.service

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.contracts.user.model.EnrollMfaRequest
import org.iamsso.contracts.user.model.MfaFactorStatus as ContractMfaFactorStatus
import org.iamsso.contracts.user.model.MfaFactorType as ContractMfaFactorType
import org.iamsso.services.userservice.entity.MfaFactorEntity
import org.iamsso.services.userservice.entity.MfaFactorStatus
import org.iamsso.services.userservice.entity.MfaFactorType
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.exception.InvalidMfaCodeException
import org.iamsso.services.userservice.exception.MfaFactorAlreadyExistsException
import org.iamsso.services.userservice.repository.MfaFactorRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaServiceTest {

    @Mock lateinit var factorRepo: MfaFactorRepository
    @Mock lateinit var userRepo: UserRepository
    @Mock lateinit var events: EventPublisher

    private lateinit var service: MfaService

    @BeforeEach
    fun setUp() {
        service = MfaService(factorRepo, userRepo, events)
    }

    // ── enroll ───────────────────────────────────────────────────────────────

    @Test
    fun `enroll TOTP creates factor with secret and provisioningUri`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(id = userId, email = "a@b.com", passwordHash = "h")
        whenever(userRepo.findById(userId)).thenReturn(Optional.of(user))
        whenever(factorRepo.findByUserIdAndFactorType(userId, MfaFactorType.TOTP)).thenReturn(null)

        val result = service.enroll(userId, EnrollMfaRequest(factorType = ContractMfaFactorType.TOTP))

        assertThat(result.secret).isNotNull()
        assertThat(result.provisioningUri).contains("otpauth://totp/")
        verify(factorRepo).save(any())
    }

    @Test
    fun `enroll EMAIL_OTP creates factor and sends OTP event`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(id = userId, email = "a@b.com", passwordHash = "h")
        whenever(userRepo.findById(userId)).thenReturn(Optional.of(user))
        whenever(factorRepo.findByUserIdAndFactorType(userId, MfaFactorType.EMAIL_OTP)).thenReturn(null)

        val result = service.enroll(userId, EnrollMfaRequest(factorType = ContractMfaFactorType.EMAIL_OTP))

        assertThat(result.secret).isNull() // secret not exposed for EMAIL_OTP
        verify(events).sendEmailOtp(any(), any(), any(), any())
    }

    @Test
    fun `enroll duplicate factor type throws MfaFactorAlreadyExistsException`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(id = userId, passwordHash = "h")
        val existing = MfaFactorEntity(user = user, factorType = MfaFactorType.TOTP)
        whenever(userRepo.findById(userId)).thenReturn(Optional.of(user))
        whenever(factorRepo.findByUserIdAndFactorType(userId, MfaFactorType.TOTP)).thenReturn(existing)

        assertThatThrownBy {
            service.enroll(userId, EnrollMfaRequest(factorType = ContractMfaFactorType.TOTP))
        }.isInstanceOf(MfaFactorAlreadyExistsException::class.java)
    }

    // ── confirm ──────────────────────────────────────────────────────────────

    @Test
    fun `confirm TOTP valid code activates factor`() {
        val userId = UUID.randomUUID()
        val factorId = UUID.randomUUID()
        val user = UserEntity(id = userId, passwordHash = "h")
        val secret = DefaultSecretGenerator().generate()
        val counter = Math.floorDiv(SystemTimeProvider().time, 30L)
        val validCode = DefaultCodeGenerator().generate(secret, counter)
        val factor = MfaFactorEntity(id = factorId, user = user,
            factorType = MfaFactorType.TOTP, secret = secret)
        whenever(factorRepo.findById(factorId)).thenReturn(Optional.of(factor))

        val result = service.confirm(userId, factorId, validCode)

        assertThat(result.status).isEqualTo(ContractMfaFactorStatus.ACTIVE)
        verify(events).mfaFactorEnrolled(userId, factorId, "TOTP")
    }

    @Test
    fun `confirm TOTP invalid code throws InvalidMfaCodeException`() {
        val userId = UUID.randomUUID()
        val factorId = UUID.randomUUID()
        val user = UserEntity(id = userId, passwordHash = "h")
        val factor = MfaFactorEntity(id = factorId, user = user,
            factorType = MfaFactorType.TOTP, secret = DefaultSecretGenerator().generate())
        whenever(factorRepo.findById(factorId)).thenReturn(Optional.of(factor))

        assertThatThrownBy { service.confirm(userId, factorId, "000000") }
            .isInstanceOf(InvalidMfaCodeException::class.java)
    }

    @Test
    fun `confirm EMAIL_OTP valid code activates factor and clears secret`() {
        val userId = UUID.randomUUID()
        val factorId = UUID.randomUUID()
        val user = UserEntity(id = userId, passwordHash = "h")
        val factor = MfaFactorEntity(id = factorId, user = user,
            factorType = MfaFactorType.EMAIL_OTP, secret = "123456")
        whenever(factorRepo.findById(factorId)).thenReturn(Optional.of(factor))

        val result = service.confirm(userId, factorId, "123456")

        assertThat(result.status).isEqualTo(ContractMfaFactorStatus.ACTIVE)
        assertThat(factor.secret).isNull()
    }

    // ── remove ───────────────────────────────────────────────────────────────

    @Test
    fun `remove deletes factor and publishes event`() {
        val userId = UUID.randomUUID()
        val factorId = UUID.randomUUID()
        val user = UserEntity(id = userId, passwordHash = "h")
        val factor = MfaFactorEntity(id = factorId, user = user, factorType = MfaFactorType.TOTP)
        whenever(factorRepo.findById(factorId)).thenReturn(Optional.of(factor))

        service.remove(userId, factorId)

        verify(factorRepo).delete(factor)
        verify(events).mfaFactorRemoved(userId, factorId, "TOTP")
    }

    // ── getStatus ────────────────────────────────────────────────────────────

    @Test
    fun `getStatus returns mfaEnabled true when active factor exists`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(id = userId, passwordHash = "h")
        val factor = MfaFactorEntity(user = user, factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE)
        whenever(userRepo.existsById(userId)).thenReturn(true)
        whenever(factorRepo.findAllByUserId(userId)).thenReturn(listOf(factor))

        val result = service.getStatus(userId)

        assertThat(result.mfaEnabled).isTrue()
    }
}
