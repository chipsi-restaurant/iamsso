package org.iamsso.services.userservice.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.contracts.user.model.CreateUserRequest
import org.iamsso.contracts.user.model.UpdateUserRequest
import org.iamsso.services.userservice.config.AppProperties
import org.iamsso.services.userservice.entity.EmailVerificationTokenEntity
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserStatus
import org.iamsso.services.userservice.exception.InvalidVerificationTokenException
import org.iamsso.services.userservice.exception.RateLimitExceededException
import org.iamsso.services.userservice.exception.UserAlreadyExistsException
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.repository.EmailVerificationTokenRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
class UserServiceTest {

    @Mock lateinit var userRepo: UserRepository
    @Mock lateinit var tokenRepo: EmailVerificationTokenRepository
    @Mock lateinit var encoder: PasswordEncoder
    @Mock lateinit var events: EventPublisher

    private val props = AppProperties()
    private lateinit var service: UserService

    @BeforeEach
    fun setUp() {
        service = UserService(userRepo, tokenRepo, encoder, events, props)
        whenever(encoder.encode(any())).thenReturn("hashed-password")
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    fun `create happy path returns UserResponse and publishes event`() {
        val request = CreateUserRequest(password = "pass1234", email = "a@b.com")
        whenever(userRepo.existsByEmail("a@b.com")).thenReturn(false)

        val result = service.create(request)

        assertThat(result.email).isEqualTo("a@b.com")
        verify(events).userCreated(any(), anyOrNull(), anyOrNull(), any())
        verify(tokenRepo).save(any())
    }

    @Test
    fun `create duplicate email throws UserAlreadyExistsException`() {
        val request = CreateUserRequest(password = "pass1234", email = "dup@b.com")
        whenever(userRepo.existsByEmail("dup@b.com")).thenReturn(true)

        assertThatThrownBy { service.create(request) }
            .isInstanceOf(UserAlreadyExistsException::class.java)
    }

    @Test
    fun `create duplicate username throws UserAlreadyExistsException`() {
        val request = CreateUserRequest(password = "pass1234", username = "dupuser")
        whenever(userRepo.existsByEmail(any())).thenReturn(false)
        whenever(userRepo.existsByUsername("dupuser")).thenReturn(true)

        assertThatThrownBy { service.create(request) }
            .isInstanceOf(UserAlreadyExistsException::class.java)
    }

    // ── getById ─────────────────────────────────────────────────────────────

    @Test
    fun `getById found returns UserResponse`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, email = "x@y.com", passwordHash = "hash")
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        val result = service.getById(id)

        assertThat(result.id).isEqualTo(id)
        assertThat(result.email).isEqualTo("x@y.com")
    }

    @Test
    fun `getById not found throws UserNotFoundException`() {
        val id = UUID.randomUUID()
        whenever(userRepo.findById(id)).thenReturn(Optional.empty())

        assertThatThrownBy { service.getById(id) }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    // ── update ──────────────────────────────────────────────────────────────

    @Test
    fun `update email resets emailVerified and publishes event`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, email = "old@b.com", emailVerified = true, passwordHash = "h")
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(userRepo.existsByEmail("new@b.com")).thenReturn(false)

        service.update(id, UpdateUserRequest(email = "new@b.com"))

        assertThat(user.email).isEqualTo("new@b.com")
        assertThat(user.emailVerified).isFalse()
        verify(events).userUpdated(any(), any())
    }

    @Test
    fun `update with duplicate email throws UserAlreadyExistsException`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, email = "old@b.com", passwordHash = "h")
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))
        whenever(userRepo.existsByEmail("dup@b.com")).thenReturn(true)

        assertThatThrownBy { service.update(id, UpdateUserRequest(email = "dup@b.com")) }
            .isInstanceOf(UserAlreadyExistsException::class.java)
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete sets status to DELETED and publishes event`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h")
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        service.delete(id)

        assertThat(user.status).isEqualTo(UserStatus.DELETED)
        verify(events).userDeleted(id)
    }

    // ── verifyEmail ──────────────────────────────────────────────────────────

    @Test
    fun `verifyEmail valid token activates user and returns UserResponse`() {
        val userId = UUID.randomUUID()
        val tokenValue = "valid-token"
        val tokenEntity = EmailVerificationTokenEntity(
            userId = userId,
            token = tokenValue,
            expiresAt = Instant.now().plusSeconds(3600),
        )
        val user = UserEntity(id = userId, passwordHash = "h",
            status = UserStatus.PENDING_VERIFICATION)
        whenever(tokenRepo.findByToken(tokenValue)).thenReturn(tokenEntity)
        whenever(userRepo.findById(userId)).thenReturn(Optional.of(user))

        val result = service.verifyEmail(userId, tokenValue)

        assertThat(result.emailVerified).isTrue()
        verify(tokenRepo).deleteAllByUserId(userId)
    }

    @Test
    fun `verifyEmail expired token throws InvalidVerificationTokenException`() {
        val userId = UUID.randomUUID()
        val tokenEntity = EmailVerificationTokenEntity(
            userId = userId,
            token = "expired",
            expiresAt = Instant.now().minusSeconds(1),
        )
        whenever(tokenRepo.findByToken("expired")).thenReturn(tokenEntity)

        assertThatThrownBy { service.verifyEmail(userId, "expired") }
            .isInstanceOf(InvalidVerificationTokenException::class.java)
    }

    @Test
    fun `verifyEmail wrong userId throws InvalidVerificationTokenException`() {
        val userId = UUID.randomUUID()
        val tokenEntity = EmailVerificationTokenEntity(
            userId = UUID.randomUUID(), // different userId
            token = "tok",
            expiresAt = Instant.now().plusSeconds(3600),
        )
        whenever(tokenRepo.findByToken("tok")).thenReturn(tokenEntity)

        assertThatThrownBy { service.verifyEmail(userId, "tok") }
            .isInstanceOf(InvalidVerificationTokenException::class.java)
    }

    // ── resendEmailVerification ──────────────────────────────────────────────

    @Test
    fun `resendEmailVerification sends new token when no cooldown`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(id = userId, email = "a@b.com", passwordHash = "h")
        whenever(userRepo.findById(userId)).thenReturn(Optional.of(user))
        whenever(tokenRepo.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(null)

        service.resendEmailVerification(userId)

        verify(tokenRepo).deleteAllByUserId(userId)
        verify(tokenRepo).save(any())
        verify(events).sendEmailVerification(any(), any(), any(), any())
    }

    @Test
    fun `resendEmailVerification throws RateLimitExceededException within cooldown`() {
        val userId = UUID.randomUUID()
        val user = UserEntity(id = userId, email = "a@b.com", passwordHash = "h")
        val recentToken = EmailVerificationTokenEntity(
            userId = userId,
            token = "recent",
            expiresAt = Instant.now().plusSeconds(3600),
            createdAt = Instant.now().minusSeconds(30), // 30s ago, cooldown is 60s
        )
        whenever(userRepo.findById(userId)).thenReturn(Optional.of(user))
        whenever(tokenRepo.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(recentToken)

        assertThatThrownBy { service.resendEmailVerification(userId) }
            .isInstanceOf(RateLimitExceededException::class.java)
    }
}
