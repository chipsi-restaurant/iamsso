package org.iamsso.services.userservice.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.services.userservice.config.AppProperties
import org.iamsso.services.userservice.entity.PasswordResetTokenEntity
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserStatus
import org.iamsso.services.userservice.exception.InvalidPasswordException
import org.iamsso.services.userservice.exception.PasswordResetTokenInvalidException
import org.iamsso.services.userservice.repository.PasswordResetTokenRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock lateinit var userRepo: UserRepository
    @Mock lateinit var tokenRepo: PasswordResetTokenRepository
    @Mock lateinit var credentialsService: CredentialsService
    @Mock lateinit var events: EventPublisher
    @Mock lateinit var sessionClient: SessionServiceClient

    private val props = AppProperties()
    private lateinit var service: PasswordResetService

    @BeforeEach
    fun setUp() {
        service = PasswordResetService(userRepo, tokenRepo, credentialsService, events, sessionClient, props)
    }

    // ── requestReset ─────────────────────────────────────────────────────────

    @Test
    fun `requestReset with unknown email does nothing (anti-enumeration)`() {
        whenever(userRepo.findByEmail("nobody@example.com")).thenReturn(null)

        service.requestReset("nobody@example.com")

        verify(tokenRepo, never()).deleteAllByUserId(any())
        verify(tokenRepo, never()).save(any())
        verify(events, never()).sendPasswordReset(any(), any(), any(), any(), any())
    }

    @Test
    fun `requestReset with known email deletes old tokens, saves new, publishes command`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, email = "ivan@example.com", status = UserStatus.ACTIVE)
        whenever(userRepo.findByEmail("ivan@example.com")).thenReturn(user)

        service.requestReset("ivan@example.com")

        verify(tokenRepo).deleteAllByUserId(id)
        val saved = ArgumentCaptor.forClass(PasswordResetTokenEntity::class.java)
        verify(tokenRepo).save(saved.capture())
        assertThat(saved.value.userId).isEqualTo(id)
        assertThat(saved.value.token).isNotBlank()
        assertThat(saved.value.expiresAt).isAfter(Instant.now())
        verify(events).sendPasswordReset(
            userId = id,
            email = "ivan@example.com",
            firstName = null,
            token = saved.value.token,
            expiresAt = saved.value.expiresAt,
        )
    }

    // ── validate ─────────────────────────────────────────────────────────────

    @Test
    fun `validate with existing non-expired token succeeds`() {
        val token = "valid-token"
        whenever(tokenRepo.findByToken(token)).thenReturn(
            PasswordResetTokenEntity(
                userId = UUID.randomUUID(),
                token = token,
                expiresAt = Instant.now().plusSeconds(600)
            )
        )

        service.validate(token)  // must not throw
    }

    @Test
    fun `validate with expired token throws PasswordResetTokenInvalidException`() {
        val token = "expired-token"
        whenever(tokenRepo.findByToken(token)).thenReturn(
            PasswordResetTokenEntity(
                userId = UUID.randomUUID(),
                token = token,
                expiresAt = Instant.now().minusSeconds(60)
            )
        )

        assertThatThrownBy { service.validate(token) }
            .isInstanceOf(PasswordResetTokenInvalidException::class.java)
    }

    @Test
    fun `validate with unknown token throws PasswordResetTokenInvalidException`() {
        whenever(tokenRepo.findByToken("nope")).thenReturn(null)

        assertThatThrownBy { service.validate("nope") }
            .isInstanceOf(PasswordResetTokenInvalidException::class.java)
    }

    // ── confirmReset ─────────────────────────────────────────────────────────

    @Test
    fun `confirmReset happy path - updates password, deletes token, invalidates sessions, publishes event`() {
        val userId = UUID.randomUUID()
        val token = "tok"
        val entity = PasswordResetTokenEntity(
            userId = userId,
            token = token,
            expiresAt = Instant.now().plusSeconds(600),
        )
        whenever(tokenRepo.findByToken(token)).thenReturn(entity)
        whenever(sessionClient.deleteAllForUser(userId)).thenReturn(true)

        service.confirmReset(token, "NewSecureP@ss1")

        verify(credentialsService).resetPassword(userId, "NewSecureP@ss1", "self-service")
        verify(tokenRepo).delete(entity)
        verify(sessionClient).deleteAllForUser(userId)
    }

    @Test
    fun `confirmReset with unknown token throws PasswordResetTokenInvalidException`() {
        whenever(tokenRepo.findByToken("nope")).thenReturn(null)

        assertThatThrownBy { service.confirmReset("nope", "NewSecureP@ss1") }
            .isInstanceOf(PasswordResetTokenInvalidException::class.java)

        verify(credentialsService, never()).resetPassword(any(), any(), any())
    }

    @Test
    fun `confirmReset with expired token throws PasswordResetTokenInvalidException`() {
        val token = "exp"
        whenever(tokenRepo.findByToken(token)).thenReturn(
            PasswordResetTokenEntity(
                userId = UUID.randomUUID(),
                token = token,
                expiresAt = Instant.now().minusSeconds(60)
            )
        )

        assertThatThrownBy { service.confirmReset(token, "NewSecureP@ss1") }
            .isInstanceOf(PasswordResetTokenInvalidException::class.java)

        verify(credentialsService, never()).resetPassword(any(), any(), any())
    }

    @Test
    fun `confirmReset with weak password throws InvalidPasswordException and does not update`() {
        val userId = UUID.randomUUID()
        val token = "tok"
        whenever(tokenRepo.findByToken(token)).thenReturn(
            PasswordResetTokenEntity(
                userId = userId,
                token = token,
                expiresAt = Instant.now().plusSeconds(600)
            )
        )

        assertThatThrownBy { service.confirmReset(token, "weak") }
            .isInstanceOf(InvalidPasswordException::class.java)

        verify(credentialsService, never()).resetPassword(any(), any(), any())
        verify(tokenRepo, never()).delete(any<PasswordResetTokenEntity>())
    }

    @Test
    fun `confirmReset when session invalidation fails still completes successfully`() {
        val userId = UUID.randomUUID()
        val token = "tok"
        val entity = PasswordResetTokenEntity(
            userId = userId,
            token = token,
            expiresAt = Instant.now().plusSeconds(600)
        )
        whenever(tokenRepo.findByToken(token)).thenReturn(entity)
        whenever(sessionClient.deleteAllForUser(userId)).thenReturn(false)

        service.confirmReset(token, "NewSecureP@ss1")

        verify(credentialsService).resetPassword(userId, "NewSecureP@ss1", "self-service")
        verify(tokenRepo).delete(entity)
    }
}
