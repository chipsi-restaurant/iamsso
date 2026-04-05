# User Service Completion — Design Spec

**Date:** 2026-04-06  
**Scope:** Add missing email-verification endpoints + full unit/integration test coverage for user-service.

---

## Context

`user-service` has all entities, repositories, services, and controllers implemented. Two gaps remain:

1. `UserService.verifyEmail()` and `UserService.resendEmailVerification()` are implemented but not exposed via any HTTP endpoint.
2. No tests exist beyond an empty `contextLoads()`.

---

## 1. New Endpoints — EmailVerificationController

New controller: `controller/EmailVerificationController.kt`

```
GET  /api/v1/users/{userId}/verify-email?token={token}
     → 200 UserResponse
     → 400 INVALID_VERIFICATION_TOKEN (expired or wrong userId)

POST /api/v1/users/{userId}/verify-email/resend
     → 204 No Content
     → 404 USER_NOT_FOUND
     → 429 RATE_LIMIT_EXCEEDED (resend cooldown not elapsed)
```

**GET** — public endpoint. Called when user clicks the link from the verification email. Delegates to `UserService.verifyEmail(userId, token)`.

**POST** — internal endpoint (no extra auth — user-service is internal). Delegates to `UserService.resendEmailVerification(userId)`.

`SecurityConfig` is unchanged — all endpoints are already `permitAll()`.

---

## 2. Test Infrastructure

### build.gradle.kts additions (testImplementation)

```kotlin
testImplementation("com.h2database:h2")
testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
testImplementation("org.springframework.kafka:spring-kafka-test")
```

### src/test/resources/application-test.yaml

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

---

## 3. Unit Tests

All unit tests use `@ExtendWith(MockitoExtension::class)` + `@MockitoSettings(strictness = Strictness.LENIENT)`. No Spring context.

### UserServiceTest
- `create` — happy path, duplicate email throws `UserAlreadyExistsException`, duplicate username throws
- `getById` — found, not found throws `UserNotFoundException`
- `update` — changes email/username/displayName/locale, duplicate email throws
- `delete` — sets status to DELETED, publishes event
- `verifyEmail` — valid token, expired token throws, wrong userId throws
- `resendEmailVerification` — happy path, rate limit throws `RateLimitExceededException`

### CredentialsServiceTest
- `verify` — valid password resets failedAttempts, invalid increments, lockout after maxFailedAttempts
- `verify` — locked account (lockedUntil in future) returns valid=false without checking password
- `changePassword` — wrong current password throws `InvalidCredentialsException`
- `resetPassword` — resets hash, clears lockout, unlocks LOCKED status

### MfaServiceTest
- `enroll TOTP` — creates factor, returns secret + provisioningUri
- `enroll EMAIL_OTP` — creates factor, sends OTP event, no secret in response
- `enroll duplicate` — throws `MfaFactorAlreadyExistsException`
- `confirm TOTP` — valid code activates factor, invalid throws `InvalidMfaCodeException`
- `confirm EMAIL_OTP` — code matches secret activates, clears secret after activation
- `remove` — deletes factor, publishes event
- `getStatus` — reflects active factors count

### UserProfileServiceTest
- `get` — user not found throws, returns profile (even if profile is null)
- `update` — creates profile if missing, updates fields, publishes event

---

## 4. MockMvc Tests (standaloneSetup)

All controller tests use `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler()).build()`. Services are `@Mock`.

### UserControllerTest
- `POST /api/v1/users` → 201 with UserResponse body
- `GET /api/v1/users/{userId}` → 200
- `GET /api/v1/users/{userId}` (not found) → 404 with error body
- `PATCH /api/v1/users/{userId}` → 200
- `DELETE /api/v1/users/{userId}` → 204
- `PUT /api/v1/users/{userId}/status` → 200

### CredentialsControllerTest
- `POST /verify` → 200 with CredentialsVerificationResponse
- `PUT /password` → 204
- `POST /password/reset` → 204

### MfaControllerTest
- `GET /factors` → 200 list
- `POST /factors` → 201 MfaEnrollmentResponse
- `POST /factors/{factorId}/verify` → 200 (valid code)
- `POST /factors/{factorId}/verify` (invalid code) → 400
- `DELETE /factors/{factorId}` → 204
- `GET /status` → 200 MfaStatusResponse

### UserProfileControllerTest
- `GET /api/v1/users/{userId}/profile` → 200
- `PATCH /api/v1/users/{userId}/profile` → 200

### EmailVerificationControllerTest
- `GET /verify-email?token=valid` → 200 UserResponse
- `GET /verify-email?token=invalid` → 400 INVALID_VERIFICATION_TOKEN
- `POST /verify-email/resend` → 204
- `POST /verify-email/resend` (rate limit) → 429 RATE_LIMIT_EXCEEDED

---

## 5. Integration Test

`UserServiceApplicationTests`:
```kotlin
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = ["user-events", "credentials-events", "mfa-events", "notification-commands"])
class UserServiceApplicationTests {
    @Test fun contextLoads() {}
}
```

---

## 6. File Map

**New files:**
- `controller/EmailVerificationController.kt`
- `test/.../controller/EmailVerificationControllerTest.kt`
- `test/.../service/UserServiceTest.kt`
- `test/.../service/CredentialsServiceTest.kt`
- `test/.../service/MfaServiceTest.kt`
- `test/.../service/UserProfileServiceTest.kt`
- `test/.../controller/UserControllerTest.kt`
- `test/.../controller/CredentialsControllerTest.kt`
- `test/.../controller/MfaControllerTest.kt`
- `test/.../controller/UserProfileControllerTest.kt`
- `src/test/resources/application-test.yaml`

**Modified files:**
- `build.gradle.kts` — add test dependencies
- `test/.../UserServiceApplicationTests.kt` — add `@ActiveProfiles("test")` + `@EmbeddedKafka`

---

## Estimated Test Count

~40 test methods across 9 test classes.
