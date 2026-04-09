package org.iamsso.services.mfaservice.service

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.iamsso.contracts.mfa.model.EnrollMfaFactorRequest
import org.iamsso.contracts.mfa.model.MfaFactorType as ContractMfaFactorType
import org.iamsso.contracts.mfa.model.VerifyMfaRequest
import org.iamsso.services.mfaservice.config.AppProperties
import org.iamsso.services.mfaservice.entity.MfaFactorEntity
import org.iamsso.services.mfaservice.entity.MfaFactorStatus
import org.iamsso.services.mfaservice.entity.MfaFactorType
import org.iamsso.services.mfaservice.exception.InvalidMfaCodeException
import org.iamsso.services.mfaservice.exception.MfaFactorAlreadyExistsException
import org.iamsso.services.mfaservice.repository.MfaFactorRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MfaServiceTest {

    @Mock
    lateinit var factorRepo: MfaFactorRepository

    @Mock
    lateinit var events: EventPublisher

    private val props = AppProperties(
        totp = AppProperties.TotpProps(issuer = "IAMSSO"),
        emailOtp = AppProperties.EmailOtpProps(ttlSeconds = 300),
    )

    lateinit var mfaService: MfaService

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mfaService = MfaService(factorRepo, events, props)
    }

    // --- listFactors ---

    @Test
    fun `listFactors returns list of responses`() {
        val entity = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE,
            displayName = "My TOTP",
            secret = "SECRET",
        )
        whenever(factorRepo.findAllByUserId(userId)).thenReturn(listOf(entity))

        val result = mfaService.listFactors(userId)

        assertEquals(1, result.size)
        assertEquals(entity.id, result[0].id)
        assertEquals(ContractMfaFactorType.TOTP, result[0].factorType)
    }

    // --- enroll ---

    @Test
    fun `enroll TOTP creates factor with secret and provisioningUri`() {
        val request = EnrollMfaFactorRequest(factorType = ContractMfaFactorType.TOTP, displayName = "My TOTP")
        whenever(factorRepo.findByUserIdAndFactorType(userId, MfaFactorType.TOTP)).thenReturn(null)
        whenever(factorRepo.save(any<MfaFactorEntity>())).thenAnswer { it.arguments[0] }

        val result = mfaService.enroll(userId, request, "user@example.com")

        assertNotNull(result.secret)
        assertNotNull(result.provisioningUri)
        assertTrue(result.provisioningUri!!.startsWith("otpauth://totp/"))
        assertTrue(result.provisioningUri!!.contains("user@example.com"))
    }

    @Test
    fun `enroll EMAIL_OTP sends OTP event`() {
        val request = EnrollMfaFactorRequest(factorType = ContractMfaFactorType.EMAIL_OTP, displayName = "My Email")
        whenever(factorRepo.findByUserIdAndFactorType(userId, MfaFactorType.EMAIL_OTP)).thenReturn(null)
        whenever(factorRepo.save(any<MfaFactorEntity>())).thenAnswer { it.arguments[0] }

        val result = mfaService.enroll(userId, request, "user@example.com")

        assertNull(result.secret)
        assertNull(result.provisioningUri)
        verify(events).sendEmailOtp(
            eq(userId),
            eq("user@example.com"),
            any(),
            any(),
        )
    }

    @Test
    fun `enroll duplicate factor throws MfaFactorAlreadyExistsException`() {
        val existing = MfaFactorEntity(userId = userId, factorType = MfaFactorType.TOTP)
        whenever(factorRepo.findByUserIdAndFactorType(userId, MfaFactorType.TOTP)).thenReturn(existing)

        val request = EnrollMfaFactorRequest(factorType = ContractMfaFactorType.TOTP)

        assertThrows<MfaFactorAlreadyExistsException> {
            mfaService.enroll(userId, request, null)
        }
    }

    // --- confirm ---

    @Test
    fun `confirm TOTP valid code activates factor`() {
        val secretGen = DefaultSecretGenerator()
        val secret = secretGen.generate()
        val codeGen = DefaultCodeGenerator()
        val timeProvider = SystemTimeProvider()
        val validCode = codeGen.generate(secret, Math.floorDiv(timeProvider.time, 30))

        val factor = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.PENDING,
            secret = secret,
        )
        whenever(factorRepo.findById(factor.id)).thenReturn(Optional.of(factor))

        val result = mfaService.confirm(userId, factor.id, validCode)

        assertEquals(org.iamsso.contracts.mfa.model.MfaFactorStatus.ACTIVE, result.status)
        verify(events).mfaFactorEnrolled(userId, factor.id, "TOTP")
    }

    @Test
    fun `confirm invalid code throws InvalidMfaCodeException`() {
        val factor = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.PENDING,
            secret = DefaultSecretGenerator().generate(),
        )
        whenever(factorRepo.findById(factor.id)).thenReturn(Optional.of(factor))

        assertThrows<InvalidMfaCodeException> {
            mfaService.confirm(userId, factor.id, "000000")
        }
    }

    @Test
    fun `confirm EMAIL_OTP valid code activates and clears secret`() {
        val code = "123456"
        val factor = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.EMAIL_OTP,
            status = MfaFactorStatus.PENDING,
            secret = code,
        )
        whenever(factorRepo.findById(factor.id)).thenReturn(Optional.of(factor))

        val result = mfaService.confirm(userId, factor.id, code)

        assertEquals(org.iamsso.contracts.mfa.model.MfaFactorStatus.ACTIVE, result.status)
        assertNull(factor.secret)
        verify(events).mfaFactorEnrolled(userId, factor.id, "EMAIL_OTP")
    }

    // --- remove ---

    @Test
    fun `remove deletes factor and publishes event`() {
        val factor = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE,
        )
        whenever(factorRepo.findById(factor.id)).thenReturn(Optional.of(factor))

        mfaService.remove(userId, factor.id)

        verify(factorRepo).delete(factor)
        verify(events).mfaFactorRemoved(userId, factor.id, "TOTP")
    }

    // --- getStatus ---

    @Test
    fun `getStatus returns mfaEnabled true when active factor exists`() {
        val factor = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE,
        )
        whenever(factorRepo.findAllByUserId(userId)).thenReturn(listOf(factor))

        val result = mfaService.getStatus(userId)

        assertTrue(result.mfaEnabled!!)
        assertEquals(listOf(ContractMfaFactorType.TOTP), result.activeFactors)
    }

    @Test
    fun `getStatus returns mfaEnabled false when no factors`() {
        whenever(factorRepo.findAllByUserId(userId)).thenReturn(emptyList())

        val result = mfaService.getStatus(userId)

        assertFalse(result.mfaEnabled!!)
        assertTrue(result.activeFactors!!.isEmpty())
    }

    // --- verify ---

    @Test
    fun `verify valid TOTP returns valid true`() {
        val secretGen = DefaultSecretGenerator()
        val secret = secretGen.generate()
        val codeGen = DefaultCodeGenerator()
        val timeProvider = SystemTimeProvider()
        val validCode = codeGen.generate(secret, Math.floorDiv(timeProvider.time, 30))

        val factor = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE,
            secret = secret,
        )
        whenever(factorRepo.findAllByUserId(userId)).thenReturn(listOf(factor))

        val result = mfaService.verify(userId, VerifyMfaRequest(code = validCode))

        assertTrue(result.valid!!)
    }

    @Test
    fun `verify invalid code returns valid false`() {
        val factor = MfaFactorEntity(
            userId = userId,
            factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.ACTIVE,
            secret = DefaultSecretGenerator().generate(),
        )
        whenever(factorRepo.findAllByUserId(userId)).thenReturn(listOf(factor))

        val result = mfaService.verify(userId, VerifyMfaRequest(code = "000000"))

        assertFalse(result.valid!!)
    }
}
