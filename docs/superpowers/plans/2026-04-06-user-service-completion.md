# User Service Completion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add missing `EmailVerificationController` endpoints and ~40 unit/MockMvc/integration tests for user-service.

**Architecture:** One new controller (`EmailVerificationController`) delegates to existing `UserService` methods. Unit tests use `MockitoExtension` without Spring context; services are instantiated manually to avoid `@Mock` on `AppProperties`. MockMvc tests use `standaloneSetup` with a custom Jackson message converter and `GlobalExceptionHandler`. Integration test uses `@EmbeddedKafka` + H2 `MODE=PostgreSQL`.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5, Mockito-Kotlin 5.4, H2 2.x, spring-kafka-test

---

## File Map

**New files (production):**
- `services/user-service/src/main/kotlin/org/iamsso/services/userservice/controller/EmailVerificationController.kt`

**New files (test):**
- `services/user-service/src/test/resources/application-test.yaml`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/UserServiceTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/CredentialsServiceTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/MfaServiceTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/UserProfileServiceTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/UserControllerTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/CredentialsControllerTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/MfaControllerTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/UserProfileControllerTest.kt`
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/EmailVerificationControllerTest.kt`

**Modified files:**
- `services/user-service/build.gradle.kts` — add 3 test dependencies
- `services/user-service/src/test/kotlin/org/iamsso/services/userservice/UserServiceApplicationTests.kt` — add `@ActiveProfiles("test")` + `@EmbeddedKafka`

---

### Task 1: Test Infrastructure

**Files:**
- Modify: `services/user-service/build.gradle.kts`
- Create: `services/user-service/src/test/resources/application-test.yaml`

- [ ] **Step 1: Add test dependencies to build.gradle.kts**

Replace the test dependencies block (lines 48–51) with:

```kotlin
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.h2database:h2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

- [ ] **Step 2: Create application-test.yaml**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        default_schema: iamsso_users
  liquibase:
    enabled: false
  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers}
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew :services:user-service:compileTestKotlin`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add services/user-service/build.gradle.kts \
        services/user-service/src/test/resources/application-test.yaml
git commit -m "test(user-service): add H2, mockito-kotlin, spring-kafka-test dependencies"
```

---

### Task 2: EmailVerificationController

**Files:**
- Create: `services/user-service/src/main/kotlin/org/iamsso/services/userservice/controller/EmailVerificationController.kt`

- [ ] **Step 1: Write the controller**

```kotlin
package org.iamsso.services.userservice.controller

import org.iamsso.services.userservice.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/{userId}/verify-email")
class EmailVerificationController(private val userService: UserService) {

    @GetMapping
    fun verifyEmail(
        @PathVariable userId: UUID,
        @RequestParam token: String,
    ) = userService.verifyEmail(userId, token)

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resend(@PathVariable userId: UUID) = userService.resendEmailVerification(userId)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :services:user-service:compileKotlin`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/main/kotlin/org/iamsso/services/userservice/controller/EmailVerificationController.kt
git commit -m "feat(user-service): add EmailVerificationController (GET verify-email, POST resend)"
```

---

### Task 3: UserServiceTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/UserServiceTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
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
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.UserServiceTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/UserServiceTest.kt
git commit -m "test(user-service): add UserServiceTest (12 test methods)"
```

---

### Task 4: CredentialsServiceTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/CredentialsServiceTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
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
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.CredentialsServiceTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/CredentialsServiceTest.kt
git commit -m "test(user-service): add CredentialsServiceTest (7 test methods)"
```

---

### Task 5: MfaServiceTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/MfaServiceTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
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
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.MfaServiceTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/MfaServiceTest.kt
git commit -m "test(user-service): add MfaServiceTest (8 test methods)"
```

---

### Task 6: UserProfileServiceTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/UserProfileServiceTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package org.iamsso.services.userservice.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.contracts.user.model.UpdateProfileRequest
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserProfileEntity
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.repository.UserProfileRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceTest {

    @Mock lateinit var userRepo: UserRepository
    @Mock lateinit var profileRepo: UserProfileRepository
    @Mock lateinit var events: EventPublisher

    private lateinit var service: UserProfileService

    @BeforeEach
    fun setUp() {
        service = UserProfileService(userRepo, profileRepo, events)
    }

    // ── get ──────────────────────────────────────────────────────────────────

    @Test
    fun `get user not found throws UserNotFoundException`() {
        val id = UUID.randomUUID()
        whenever(userRepo.findById(id)).thenReturn(Optional.empty())

        assertThatThrownBy { service.get(id) }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun `get returns profile response even when profile is null`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h", displayName = "Alice")
        user.profile = null
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        val result = service.get(id)

        assertThat(result.userId).isEqualTo(id)
        assertThat(result.firstName).isNull()
    }

    @Test
    fun `get returns full profile when profile exists`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h")
        val profile = UserProfileEntity(user = user, firstName = "Alice", timezone = "UTC")
        user.profile = profile
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        val result = service.get(id)

        assertThat(result.firstName).isEqualTo("Alice")
        assertThat(result.timezone).isEqualTo("UTC")
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    fun `update creates profile if missing and publishes event`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h")
        user.profile = null
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        service.update(id, UpdateProfileRequest(firstName = "Bob"))

        verify(profileRepo).save(any())
        verify(events).userProfileUpdated(any(), any())
    }

    @Test
    fun `update updates fields on existing profile`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h")
        val profile = UserProfileEntity(user = user, firstName = "Old")
        user.profile = profile
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        service.update(id, UpdateProfileRequest(firstName = "New", timezone = "Europe/Moscow"))

        assertThat(profile.firstName).isEqualTo("New")
        assertThat(profile.timezone).isEqualTo("Europe/Moscow")
        verify(events).userProfileUpdated(any(), any())
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.UserProfileServiceTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/service/UserProfileServiceTest.kt
git commit -m "test(user-service): add UserProfileServiceTest (5 test methods)"
```

---

### Task 7: UserControllerTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/UserControllerTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.ChangeStatusRequest
import org.iamsso.contracts.user.model.CreateUserRequest
import org.iamsso.contracts.user.model.UpdateUserRequest
import org.iamsso.contracts.user.model.UserResponse
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerTest {

    @Mock lateinit var userService: UserService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val sampleUser = UserResponse(
        id = userId, email = "a@b.com", username = "alice",
        status = UserStatus.ACTIVE, emailVerified = true, mfaEnabled = false,
        locale = "en", createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(UserController(userService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST api v1 users returns 201 with UserResponse`() {
        val request = CreateUserRequest(password = "pass1234", email = "a@b.com")
        whenever(userService.create(any())).thenReturn(sampleUser)

        mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("a@b.com") }
        }
    }

    @Test
    fun `GET api v1 users userId returns 200`() {
        whenever(userService.getById(userId)).thenReturn(sampleUser)

        mockMvc.get("/api/v1/users/$userId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(userId.toString()) }
            }
    }

    @Test
    fun `GET api v1 users userId not found returns 404`() {
        whenever(userService.getById(userId)).thenThrow(UserNotFoundException(userId))

        mockMvc.get("/api/v1/users/$userId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("USER_NOT_FOUND") }
            }
    }

    @Test
    fun `PATCH api v1 users userId returns 200`() {
        whenever(userService.update(any(), any())).thenReturn(sampleUser)

        mockMvc.patch("/api/v1/users/$userId") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(UpdateUserRequest(displayName = "Alice"))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `DELETE api v1 users userId returns 204`() {
        mockMvc.delete("/api/v1/users/$userId")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `PUT api v1 users userId status returns 200`() {
        val request = ChangeStatusRequest(status = UserStatus.SUSPENDED, reason = "admin decision")
        whenever(userService.changeStatus(any(), any())).thenReturn(sampleUser)

        mockMvc.put("/api/v1/users/$userId/status") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.UserControllerTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/UserControllerTest.kt
git commit -m "test(user-service): add UserControllerTest (6 test methods)"
```

---

### Task 8: CredentialsControllerTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/CredentialsControllerTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.ChangePasswordRequest
import org.iamsso.contracts.user.model.CredentialsVerificationResponse
import org.iamsso.contracts.user.model.ResetPasswordRequest
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.contracts.user.model.VerifyCredentialsRequest
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.service.CredentialsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredentialsControllerTest {

    @Mock lateinit var credentialsService: CredentialsService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(CredentialsController(credentialsService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST credentials verify returns 200 with CredentialsVerificationResponse`() {
        val response = CredentialsVerificationResponse(valid = true, userId = userId,
            status = UserStatus.ACTIVE, mfaRequired = false, failedAttempts = 0)
        whenever(credentialsService.verify(any(), any(), anyOrNull(), anyOrNull())).thenReturn(response)

        mockMvc.post("/api/v1/users/$userId/credentials/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(VerifyCredentialsRequest(password = "pass"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
        }
    }

    @Test
    fun `PUT credentials password returns 204`() {
        mockMvc.put("/api/v1/users/$userId/credentials/password") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(
                ChangePasswordRequest(currentPassword = "old", newPassword = "new"))
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `POST credentials password reset returns 204`() {
        mockMvc.post("/api/v1/users/$userId/credentials/password/reset") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(ResetPasswordRequest(newPassword = "new"))
        }.andExpect {
            status { isNoContent() }
        }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.CredentialsControllerTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/CredentialsControllerTest.kt
git commit -m "test(user-service): add CredentialsControllerTest (3 test methods)"
```

---

### Task 9: MfaControllerTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/MfaControllerTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.EnrollMfaRequest
import org.iamsso.contracts.user.model.MfaEnrollmentResponse
import org.iamsso.contracts.user.model.MfaFactorResponse
import org.iamsso.contracts.user.model.MfaFactorStatus
import org.iamsso.contracts.user.model.MfaFactorType
import org.iamsso.contracts.user.model.MfaStatusResponse
import org.iamsso.contracts.user.model.VerifyMfaCodeRequest
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.exception.InvalidMfaCodeException
import org.iamsso.services.userservice.service.MfaService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MfaControllerTest {

    @Mock lateinit var mfaService: MfaService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val factorId = UUID.randomUUID()
    private val sampleFactor = MfaFactorResponse(
        id = factorId, factorType = MfaFactorType.TOTP,
        status = MfaFactorStatus.ACTIVE, createdAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(MfaController(mfaService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `GET mfa factors returns 200 list`() {
        whenever(mfaService.listFactors(userId)).thenReturn(listOf(sampleFactor))

        mockMvc.get("/api/v1/users/$userId/mfa/factors")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].factorType") { value("TOTP") }
            }
    }

    @Test
    fun `POST mfa factors returns 201 with MfaEnrollmentResponse`() {
        val enrollment = MfaEnrollmentResponse(
            factorId = factorId, factorType = MfaFactorType.TOTP,
            status = MfaFactorStatus.PENDING, secret = "SECRET",
        )
        whenever(mfaService.enroll(any(), any())).thenReturn(enrollment)

        mockMvc.post("/api/v1/users/$userId/mfa/factors") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(EnrollMfaRequest(factorType = MfaFactorType.TOTP))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.secret") { value("SECRET") }
        }
    }

    @Test
    fun `POST mfa factors factorId verify valid code returns 200`() {
        whenever(mfaService.confirm(any(), any(), any())).thenReturn(sampleFactor)

        mockMvc.post("/api/v1/users/$userId/mfa/factors/$factorId/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(VerifyMfaCodeRequest(code = "123456"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `POST mfa factors factorId verify invalid code returns 400`() {
        whenever(mfaService.confirm(any(), any(), any())).thenThrow(InvalidMfaCodeException())

        mockMvc.post("/api/v1/users/$userId/mfa/factors/$factorId/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(VerifyMfaCodeRequest(code = "000000"))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_MFA_CODE") }
        }
    }

    @Test
    fun `DELETE mfa factors factorId returns 204`() {
        mockMvc.delete("/api/v1/users/$userId/mfa/factors/$factorId")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `GET mfa status returns 200 with MfaStatusResponse`() {
        whenever(mfaService.getStatus(userId)).thenReturn(
            MfaStatusResponse(userId = userId, mfaEnabled = false, activeFactors = emptyList())
        )

        mockMvc.get("/api/v1/users/$userId/mfa/status")
            .andExpect {
                status { isOk() }
                jsonPath("$.mfaEnabled") { value(false) }
            }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.MfaControllerTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/MfaControllerTest.kt
git commit -m "test(user-service): add MfaControllerTest (6 test methods)"
```

---

### Task 10: UserProfileControllerTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/UserProfileControllerTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.UpdateProfileRequest
import org.iamsso.contracts.user.model.UserProfileResponse
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.service.UserProfileService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileControllerTest {

    @Mock lateinit var userProfileService: UserProfileService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val sampleProfile = UserProfileResponse(
        userId = userId, displayName = "Alice",
        firstName = "Alice", locale = "en", updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(UserProfileController(userProfileService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `GET api v1 users userId profile returns 200`() {
        whenever(userProfileService.get(userId)).thenReturn(sampleProfile)

        mockMvc.get("/api/v1/users/$userId/profile")
            .andExpect {
                status { isOk() }
                jsonPath("$.displayName") { value("Alice") }
            }
    }

    @Test
    fun `PATCH api v1 users userId profile returns 200`() {
        whenever(userProfileService.update(any(), any())).thenReturn(sampleProfile)

        mockMvc.patch("/api/v1/users/$userId/profile") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(UpdateProfileRequest(displayName = "Alice"))
        }.andExpect {
            status { isOk() }
        }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.UserProfileControllerTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/UserProfileControllerTest.kt
git commit -m "test(user-service): add UserProfileControllerTest (2 test methods)"
```

---

### Task 11: EmailVerificationControllerTest

**Files:**
- Create: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/EmailVerificationControllerTest.kt`

- [ ] **Step 1: Write the test file**

```kotlin
package org.iamsso.services.userservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.contracts.user.model.UserResponse
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.exception.GlobalExceptionHandler
import org.iamsso.services.userservice.exception.InvalidVerificationTokenException
import org.iamsso.services.userservice.exception.RateLimitExceededException
import org.iamsso.services.userservice.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationControllerTest {

    @Mock lateinit var userService: UserService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val userId = UUID.randomUUID()
    private val verifiedUser = UserResponse(
        id = userId, email = "a@b.com", status = UserStatus.ACTIVE,
        emailVerified = true, mfaEnabled = false, locale = "en",
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(EmailVerificationController(userService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `GET verify-email with valid token returns 200 UserResponse`() {
        whenever(userService.verifyEmail(eq(userId), eq("valid-token"))).thenReturn(verifiedUser)

        mockMvc.get("/api/v1/users/$userId/verify-email") {
            param("token", "valid-token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.emailVerified") { value(true) }
        }
    }

    @Test
    fun `GET verify-email with invalid token returns 400 INVALID_VERIFICATION_TOKEN`() {
        whenever(userService.verifyEmail(any(), eq("bad-token")))
            .thenThrow(InvalidVerificationTokenException())

        mockMvc.get("/api/v1/users/$userId/verify-email") {
            param("token", "bad-token")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_VERIFICATION_TOKEN") }
        }
    }

    @Test
    fun `POST verify-email resend returns 204`() {
        mockMvc.post("/api/v1/users/$userId/verify-email/resend")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `POST verify-email resend returns 429 when rate limit exceeded`() {
        whenever(userService.resendEmailVerification(userId))
            .thenThrow(RateLimitExceededException(30L))

        mockMvc.post("/api/v1/users/$userId/verify-email/resend")
            .andExpect {
                status { isTooManyRequests() }
                jsonPath("$.code") { value("RATE_LIMIT_EXCEEDED") }
            }
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :services:user-service:test --tests "*.EmailVerificationControllerTest"`

Expected: All tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/controller/EmailVerificationControllerTest.kt
git commit -m "test(user-service): add EmailVerificationControllerTest (4 test methods)"
```

---

### Task 12: Update Integration Test

**Files:**
- Modify: `services/user-service/src/test/kotlin/org/iamsso/services/userservice/UserServiceApplicationTests.kt`

- [ ] **Step 1: Replace the integration test**

```kotlin
package org.iamsso.services.userservice

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["iam.user.events", "iam.credentials.events", "iam.mfa.events", "iam.notification.commands"],
)
class UserServiceApplicationTests {

    @Test
    fun contextLoads() {}
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew :services:user-service:test --tests "*.UserServiceApplicationTests"`

Expected: PASS, `BUILD SUCCESSFUL`

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew :services:user-service:test`

Expected: All ~42 tests PASS, `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add services/user-service/src/test/kotlin/org/iamsso/services/userservice/UserServiceApplicationTests.kt
git commit -m "test(user-service): enable contextLoads integration test with EmbeddedKafka + H2"
```
