package org.iamsso.services.userservice.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.services.userservice.config.AppProperties
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserStatus
import org.iamsso.services.userservice.exception.InvalidCredentialsException
import org.iamsso.services.userservice.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredentialsServiceTest {

    @Mock lateinit var userRepo: UserRepository
    @Mock lateinit var encoder: PasswordEncoder
    @Mock lateinit var events: EventPublisher

    private val props = AppProperties()
    private lateinit var service: CredentialsService

    @BeforeEach
    fun setUp() {
        service = CredentialsService(userRepo, encoder, events, props)
    }

    // ── verify ───────────────────────────────────────────────────────────────

    @Test
    fun `verify valid password resets failedAttempts and returns valid=true`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "hash", failedAttempts = 2)
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(encoder.matches("correct", "hash")).thenReturn(true)

        val result = service.verify(id, "correct", null, null)

        assertThat(result.valid).isTrue()
        assertThat(user.failedAttempts).isEqualTo(0)
    }

    @Test
    fun `verify invalid password increments failedAttempts and returns valid=false`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "hash", failedAttempts = 0)
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(encoder.matches("wrong", "hash")).thenReturn(false)

        val result = service.verify(id, "wrong", "1.2.3.4", "Agent")

        assertThat(result.valid).isFalse()
        assertThat(user.failedAttempts).isEqualTo(1)
        verify(events).credentialsVerifyFailed(any(), any(), any(), any())
    }

    @Test
    fun `verify locks account after maxFailedAttempts`() {
        val id = UUID.randomUUID()
        val user = UserEntity(
            id = id,
            passwordHash = "hash",
            failedAttempts = props.credentials.maxFailedAttempts - 1,
        )
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(encoder.matches("wrong", "hash")).thenReturn(false)

        service.verify(id, "wrong", null, null)

        assertThat(user.status).isEqualTo(UserStatus.LOCKED)
        assertThat(user.lockedUntil).isNotNull()
    }

    @Test
    fun `verify returns valid=false without checking password when account is locked`() {
        val id = UUID.randomUUID()
        val user = UserEntity(
            id = id,
            passwordHash = "hash",
            lockedUntil = Instant.now().plusSeconds(300),
            status = UserStatus.LOCKED,
        )
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        val result = service.verify(id, "any", null, null)

        assertThat(result.valid).isFalse()
        verify(encoder, never()).matches(any(), any())
    }

    // ── changePassword ───────────────────────────────────────────────────────

    @Test
    fun `changePassword wrong current password throws InvalidCredentialsException`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "hash")
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(encoder.matches("wrong", "hash")).thenReturn(false)

        assertThatThrownBy { service.changePassword(id, "wrong", "new") }
            .isInstanceOf(InvalidCredentialsException::class.java)
    }

    @Test
    fun `changePassword correct password updates hash and publishes event`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "old-hash")
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(encoder.matches("current", "old-hash")).thenReturn(true)
        whenever(encoder.encode("newpass")).thenReturn("new-hash")

        service.changePassword(id, "current", "newpass")

        assertThat(user.passwordHash).isEqualTo("new-hash")
        verify(events).credentialsPasswordChanged(id)
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    fun `resetPassword resets hash clears lockout and unlocks LOCKED status`() {
        val id = UUID.randomUUID()
        val user = UserEntity(
            id = id,
            passwordHash = "old-hash",
            failedAttempts = 5,
            lockedUntil = Instant.now().plusSeconds(300),
            status = UserStatus.LOCKED,
        )
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(encoder.encode("new")).thenReturn("new-hash")

        service.resetPassword(id, "new", "admin")

        assertThat(user.passwordHash).isEqualTo("new-hash")
        assertThat(user.failedAttempts).isEqualTo(0)
        assertThat(user.lockedUntil).isNull()
        assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
        verify(events).credentialsPasswordReset(id, "admin")
    }
}
