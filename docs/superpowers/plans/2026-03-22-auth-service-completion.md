# Auth Service Completion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining 20% of auth-service: authorization flow with SSO sessions, UserInfo, Client CRUD, Session management, Kafka events, and user event consumer.

**Architecture:** Two Spring Security FilterChains (OAuth/OIDC public, API admin with JWT). SSO session stored in Redis behind `SsoSessionService` interface (decoupled for future extraction to session-service). Authorization flow: `/oauth2/authorize` → frontend login → `POST /oauth2/authorize/callback` → auth code → redirect.

**Tech Stack:** Kotlin 2.1+, Spring Boot 4, Nimbus JOSE+JWT, Redis (StringRedisTemplate), Kafka (KafkaTemplate), JUnit 5, Mockito (spring-boot-starter-test), MockMvc

---

## File Map

### libs/contracts — Modified
- `src/main/kotlin/org/iamsso/contracts/events/CloudEventEnvelope.kt` — add 6 new `@JsonSubTypes` entries
- `src/main/kotlin/org/iamsso/contracts/events/KafkaTopics.kt` — add `AUTH_EVENTS`, `SESSION_EVENTS`

### libs/contracts — New
- `src/main/kotlin/org/iamsso/contracts/events/AuthEvents.kt` — 4 auth domain events
- `src/main/kotlin/org/iamsso/contracts/events/SessionEvents.kt` — 2 session domain events

### services/auth-service — Modified
- `src/main/kotlin/.../repository/AuthorizationCodeStore.kt` — fix package, add `codeChallengeMethod`
- `src/main/kotlin/.../repository/RefreshTokenRepository.kt` — fix `revokeAllBySessionId` param type
- `src/main/kotlin/.../config/AppConfig.kt` — add `LoginPageProps`, `AdminProps`, `SsoSessionProps`, `AuthorizationRequestProps`
- `src/main/kotlin/.../config/SecurityConfig.kt` — full rewrite: two `@Bean SecurityFilterChain`
- `src/main/resources/application.yaml` — fix Kafka trusted packages, add new `app.*` props

### services/auth-service — New
- `src/main/kotlin/.../service/AuthRequestService.kt` — save/get/delete pending `AuthRequest` in Redis
- `src/main/kotlin/.../service/SsoSessionService.kt` — interface
- `src/main/kotlin/.../service/RedisSsoSessionService.kt` — Redis implementation of `SsoSessionService`
- `src/main/kotlin/.../service/AuthEventPublisher.kt` — publish Kafka auth/session events
- `src/main/kotlin/.../controller/AuthorizationController.kt` — `GET /oauth2/authorize`, `POST /oauth2/authorize/callback`
- `src/main/kotlin/.../controller/UserInfoController.kt` — `GET|POST /userinfo`
- `src/main/kotlin/.../controller/ClientController.kt` — `/api/v1/clients/**`
- `src/main/kotlin/.../controller/SessionController.kt` — `/api/v1/sessions/**`
- `src/main/kotlin/.../consumer/UserKafkaConsumer.kt` — handles `user.deleted`, `user.status-changed`

### services/auth-service — Tests (New)
- `src/test/kotlin/.../service/AuthRequestServiceTest.kt`
- `src/test/kotlin/.../service/RedisSsoSessionServiceTest.kt`
- `src/test/kotlin/.../controller/AuthorizationControllerTest.kt`
- `src/test/kotlin/.../controller/UserInfoControllerTest.kt`
- `src/test/kotlin/.../controller/ClientControllerTest.kt`
- `src/test/kotlin/.../controller/SessionControllerTest.kt`
- `src/test/kotlin/.../consumer/UserKafkaConsumerTest.kt`

> **Path shorthand:** `src/main/kotlin/org/iamsso/services/authservice/` is abbreviated as `.../` below.

---

## Task 1: Fix prerequisites in existing code

**Files:**
- Modify: `services/auth-service/src/main/kotlin/.../repository/AuthorizationCodeStore.kt`
- Modify: `services/auth-service/src/main/kotlin/.../repository/RefreshTokenRepository.kt`
- Modify: `services/auth-service/src/main/resources/application.yaml`

- [ ] **Step 1.1: Fix package in AuthorizationCodeStore.kt**

Change line 1 from:
```kotlin
package com.iam.auth.repository
```
to:
```kotlin
package org.iamsso.services.authservice.repository
```

- [ ] **Step 1.2: Add `codeChallengeMethod` to AuthorizationCodeData**

In the same file, update the data class:
```kotlin
data class AuthorizationCodeData(
    val code: String,
    val clientId: String,
    val userId: UUID,
    val redirectUri: String,
    val scopes: List<String>,
    val codeChallenge: String,
    val codeChallengeMethod: String,   // NEW
    val nonce: String? = null,
    val sessionId: String? = null,
)
```

- [ ] **Step 1.3: Fix revokeAllBySessionId parameter type**

In `RefreshTokenRepository.kt`, change:
```kotlin
@Modifying
@Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.sessionId = :sessionId AND r.revoked = false")
fun revokeAllBySessionId(sessionId: String): Int
```
to:
```kotlin
@Modifying
@Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.sessionId = :sessionId AND r.revoked = false")
fun revokeAllBySessionId(sessionId: UUID): Int
```

- [ ] **Step 1.4: Fix application.yaml**

Fix the Kafka trusted packages (was `com.iam.contracts.events`) and add required new properties:
```yaml
spring:
  kafka:
    consumer:
      properties:
        spring.json.trusted.packages: "org.iamsso.contracts.events"
        spring.json.value.default.type: "org.iamsso.contracts.events.CloudEventEnvelope"

app:
  login-page:
    url: http://localhost:3000
  admin:
    scope: iam:admin
  sso-session:
    ttl-seconds: 86400
  authorization-request:
    ttl-seconds: 300
```
Keep all existing `app.*` properties, only add the new ones.

- [ ] **Step 1.5: Verify compilation**

```bash
./gradlew :services:auth-service:compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 1.6: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/repository/AuthorizationCodeStore.kt \
        services/auth-service/src/main/kotlin/org/iamsso/services/authservice/repository/RefreshTokenRepository.kt \
        services/auth-service/src/main/resources/application.yaml
git commit -m "fix: fix AuthorizationCodeStore package, add codeChallengeMethod, fix revokeAllBySessionId type"
```

---

## Task 2: Add auth/session events to contracts

**Files:**
- Create: `libs/contracts/src/main/kotlin/org/iamsso/contracts/events/AuthEvents.kt`
- Create: `libs/contracts/src/main/kotlin/org/iamsso/contracts/events/SessionEvents.kt`
- Modify: `libs/contracts/src/main/kotlin/org/iamsso/contracts/events/KafkaTopics.kt`
- Modify: `libs/contracts/src/main/kotlin/org/iamsso/contracts/events/CloudEventEnvelope.kt`

- [ ] **Step 2.1: Write failing test for new event classes**

Create `libs/contracts/src/test/kotlin/org/iamsso/contracts/events/AuthEventsTest.kt`:
```kotlin
package org.iamsso.contracts.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthEventsTest {
    private val mapper = ObjectMapper().registerKotlinModule()
        .findAndRegisterModules()

    @Test
    fun `LoginSuccessEvent serializes with correct eventType`() {
        val event = LoginSuccessEvent(
            userId = UUID.randomUUID(),
            clientId = "test-client",
            sessionId = UUID.randomUUID().toString(),
        )
        val json = mapper.writeValueAsString(event)
        assertTrue(json.contains("\"eventType\":\"auth.login-success\""))
    }

    @Test
    fun `DomainEvent deserializes LoginSuccessEvent by eventType`() {
        val event = LoginSuccessEvent(
            userId = UUID.randomUUID(),
            clientId = "test-client",
            sessionId = UUID.randomUUID().toString(),
        )
        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, DomainEvent::class.java)
        assertTrue(deserialized is LoginSuccessEvent)
        assertEquals(event.clientId, (deserialized as LoginSuccessEvent).clientId)
    }
}
```

- [ ] **Step 2.2: Run test to verify it fails**

```bash
./gradlew :libs:contracts:test --tests "org.iamsso.contracts.events.AuthEventsTest"
```
Expected: FAIL — `LoginSuccessEvent` not found

- [ ] **Step 2.3: Create AuthEvents.kt**

```kotlin
package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

data class TokenIssuedEvent(
    override val eventType: String = "auth.token-issued",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID,
    val clientId: String,
    val grantType: String,
    val scopes: List<String>,
    val sessionId: String?,
) : DomainEvent

data class TokenRevokedEvent(
    override val eventType: String = "auth.token-revoked",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID?,
    val clientId: String,
    val tokenType: String,
    val reason: String,
) : DomainEvent

data class LoginSuccessEvent(
    override val eventType: String = "auth.login-success",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID,
    val clientId: String,
    val sessionId: String,
    val mfaUsed: Boolean = false,
) : DomainEvent

data class LoginFailedEvent(
    override val eventType: String = "auth.login-failed",
    override val timestamp: Instant = Instant.now(),
    val identifier: String,
    val clientId: String,
    val reason: String,
) : DomainEvent
```

- [ ] **Step 2.4: Create SessionEvents.kt**

```kotlin
package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

data class SessionCreatedEvent(
    override val eventType: String = "session.created",
    override val timestamp: Instant = Instant.now(),
    val sessionId: String,
    val userId: UUID,
    val clientId: String,
) : DomainEvent

data class SessionDestroyedEvent(
    override val eventType: String = "session.destroyed",
    override val timestamp: Instant = Instant.now(),
    val sessionId: String,
    val userId: UUID,
    val reason: String,
) : DomainEvent
```

- [ ] **Step 2.5: Register new types in DomainEvent @JsonSubTypes**

In `CloudEventEnvelope.kt`, add to the `@JsonSubTypes` annotation on `DomainEvent`:
```kotlin
// ── Auth ──
JsonSubTypes.Type(value = TokenIssuedEvent::class,     name = "auth.token-issued"),
JsonSubTypes.Type(value = TokenRevokedEvent::class,    name = "auth.token-revoked"),
JsonSubTypes.Type(value = LoginSuccessEvent::class,    name = "auth.login-success"),
JsonSubTypes.Type(value = LoginFailedEvent::class,     name = "auth.login-failed"),
// ── Session ──
JsonSubTypes.Type(value = SessionCreatedEvent::class,  name = "session.created"),
JsonSubTypes.Type(value = SessionDestroyedEvent::class, name = "session.destroyed"),
```

- [ ] **Step 2.6: Add topics to KafkaTopics.kt**

```kotlin
/** События авторизации и выдачи токенов */
const val AUTH_EVENTS = "iam.auth.events"

/** События SSO-сессий */
const val SESSION_EVENTS = "iam.session.events"
```

- [ ] **Step 2.7: Run test to verify it passes**

```bash
./gradlew :libs:contracts:test --tests "org.iamsso.contracts.events.AuthEventsTest"
```
Expected: PASS

- [ ] **Step 2.8: Commit**

```bash
git add libs/contracts/src/main/kotlin/org/iamsso/contracts/events/ \
        libs/contracts/src/test/kotlin/org/iamsso/contracts/events/AuthEventsTest.kt
git commit -m "feat(contracts): add auth/session events and Kafka topic constants"
```

---

## Task 3: Update AppProperties and rewrite SecurityConfig

**Files:**
- Modify: `services/auth-service/src/main/kotlin/.../config/AppConfig.kt`
- Modify: `services/auth-service/src/main/kotlin/.../config/SecurityConfig.kt`

- [ ] **Step 3.1: Extend AppProperties**

Replace the content of `AppConfig.kt`:
```kotlin
package org.iamsso.services.authservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val issuer: String = "http://localhost:8080",
    val jwt: JwtProps = JwtProps(),
    val authorizationCode: AuthCodeProps = AuthCodeProps(),
    val deviceCode: DeviceCodeProps = DeviceCodeProps(),
    val userService: UserServiceProps = UserServiceProps(),
    val loginPage: LoginPageProps = LoginPageProps(),
    val admin: AdminProps = AdminProps(),
    val ssoSession: SsoSessionProps = SsoSessionProps(),
    val authorizationRequest: AuthorizationRequestProps = AuthorizationRequestProps(),
) {
    data class JwtProps(
        val accessTokenTtlSeconds: Long = 3600,
        val idTokenTtlSeconds: Long = 3600,
        val keyId: String = "iam-rsa-1",
        val rsaKeySize: Int = 2048,
    )
    data class AuthCodeProps(val ttlSeconds: Long = 60)
    data class DeviceCodeProps(
        val ttlSeconds: Long = 600,
        val pollingIntervalSeconds: Int = 5,
        val userCodeLength: Int = 8,
    )
    data class UserServiceProps(val baseUrl: String = "http://localhost:8081")
    data class LoginPageProps(val url: String = "http://localhost:3000")
    data class AdminProps(val scope: String = "iam:admin")
    data class SsoSessionProps(val ttlSeconds: Long = 86400)
    data class AuthorizationRequestProps(val ttlSeconds: Long = 300)
}
```

- [ ] **Step 3.2: Write failing test for SecurityConfig chains**

Create `src/test/kotlin/.../config/SecurityConfigTest.kt`:
```kotlin
package org.iamsso.services.authservice.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"])
class SecurityConfigTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `well-known endpoint is public`() {
        mockMvc.get("/.well-known/openid-configuration")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `api v1 endpoint requires authentication`() {
        mockMvc.get("/api/v1/clients")
            .andExpect { status { isUnauthorized() } }
    }
}
```

- [ ] **Step 3.3: Rewrite SecurityConfig**

Replace content of `SecurityConfig.kt`:
```kotlin
package org.iamsso.services.authservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(private val jwtKeyProvider: JwtKeyProvider) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /** Chain 1: Public OAuth/OIDC endpoints — /oauth2/**, /.well-known/**, /userinfo, /actuator/** */
    @Bean
    @Order(1)
    fun oauthFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/oauth2/**", "/.well-known/**", "/userinfo", "/actuator/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    /** Chain 2: Admin API endpoints — /api/v1/** — requires Bearer JWT with scope iam:admin */
    @Bean
    @Order(2)
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/api/v1/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().hasAuthority("SCOPE_iam:admin") }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.decoder(localJwtDecoder()) }
            }
            .build()

    @Bean
    fun localJwtDecoder(): NimbusJwtDecoder =
        NimbusJwtDecoder.withPublicKey(jwtKeyProvider.publicKey).build()
}
```

- [ ] **Step 3.4: Run tests**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.config.SecurityConfigTest"
```
Expected: PASS

- [ ] **Step 3.5: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/config/ \
        services/auth-service/src/test/kotlin/org/iamsso/services/authservice/config/
git commit -m "feat(auth-service): extend AppProperties, rewrite SecurityConfig with two filter chains"
```

---

## Task 4: AuthRequestService and SsoSessionService

**Files:**
- Create: `src/main/kotlin/.../service/AuthRequestService.kt`
- Create: `src/main/kotlin/.../service/SsoSessionService.kt`
- Create: `src/main/kotlin/.../service/RedisSsoSessionService.kt`
- Create: `src/test/kotlin/.../service/AuthRequestServiceTest.kt`
- Create: `src/test/kotlin/.../service/RedisSsoSessionServiceTest.kt`

- [ ] **Step 4.1: Write failing test for AuthRequestService**

Create `src/test/kotlin/.../service/AuthRequestServiceTest.kt`:
```kotlin
package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthRequestServiceTest {

    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>

    private lateinit var service: AuthRequestService
    private val mapper = ObjectMapper().registerKotlinModule().findAndRegisterModules()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(redis.opsForValue()).thenReturn(valueOps)
        service = AuthRequestService(redis, mapper)
    }

    @Test
    fun `save stores AuthRequest in Redis`() {
        val req = AuthRequest(
            authRequestId = "req-1",
            clientId = "client-1",
            redirectUri = "https://app.example.com/callback",
            scope = "openid profile",
            state = "xyz",
            codeChallenge = "abc123",
            codeChallengeMethod = "S256",
            nonce = null,
        )
        service.save(req, ttlSeconds = 300)
        verify(valueOps).set(eq("auth:request:req-1"), any(), any())
    }

    @Test
    fun `get returns null for missing key`() {
        whenever(valueOps.getAndDelete("auth:request:missing")).thenReturn(null)
        assertNull(service.getAndDelete("missing"))
    }
}
```

- [ ] **Step 4.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.AuthRequestServiceTest"
```
Expected: FAIL — `AuthRequestService` not found

- [ ] **Step 4.3: Create AuthRequestService.kt**

```kotlin
package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

data class AuthRequest(
    val authRequestId: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String?,
    val state: String?,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val nonce: String?,
)

@Service
class AuthRequestService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val prefix = "auth:request:"

    fun save(request: AuthRequest, ttlSeconds: Long) {
        val json = objectMapper.writeValueAsString(request)
        redis.opsForValue().set("$prefix${request.authRequestId}", json, Duration.ofSeconds(ttlSeconds))
    }

    fun getAndDelete(authRequestId: String): AuthRequest? {
        val json = redis.opsForValue().getAndDelete("$prefix$authRequestId") ?: return null
        return objectMapper.readValue(json, AuthRequest::class.java)
    }
}
```

- [ ] **Step 4.4: Write failing test for RedisSsoSessionService**

Create `src/test/kotlin/.../service/RedisSsoSessionServiceTest.kt`:
```kotlin
package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RedisSsoSessionServiceTest {

    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>

    private lateinit var service: RedisSsoSessionService
    private val mapper = ObjectMapper().registerKotlinModule().findAndRegisterModules()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(redis.opsForValue()).thenReturn(valueOps)
        service = RedisSsoSessionService(redis, mapper, ttlSeconds = 86400)
    }

    @Test
    fun `create stores session and returns it with firstClientId in list`() {
        val userId = UUID.randomUUID()
        val session = service.create(userId, firstClientId = "client-1")
        assertEquals(userId, session.userId)
        assertEquals(listOf("client-1"), session.clientIds)
        verify(valueOps).set(any(), any(), any())
    }

    @Test
    fun `get returns null for missing session`() {
        whenever(valueOps.get(any())).thenReturn(null)
        val result = service.get("missing-id")
        assert(result == null)
    }
}
```

- [ ] **Step 4.5: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.RedisSsoSessionServiceTest"
```
Expected: FAIL

- [ ] **Step 4.6: Create SsoSessionService interface and RedisSsoSessionService**

Create `SsoSessionService.kt`:
```kotlin
package org.iamsso.services.authservice.service

import java.time.Instant
import java.util.UUID

data class SsoSession(
    val sessionId: String,
    val userId: UUID,
    val clientIds: MutableList<String>,
    val createdAt: Instant,
    val lastActivityAt: Instant,
    val expiresAt: Instant,
)

interface SsoSessionService {
    fun create(userId: UUID, firstClientId: String): SsoSession
    fun get(sessionId: String): SsoSession?
    fun addClient(sessionId: String, clientId: String)
    fun updateActivity(sessionId: String)
    fun delete(sessionId: String)
    fun deleteAllForUser(userId: UUID)
}
```

Create `RedisSsoSessionService.kt`:
```kotlin
package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RedisSsoSessionService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val ttlSeconds: Long,
) : SsoSessionService {

    private val prefix = "sso:session:"
    private val userIndex = "sso:user:"  // Set of sessionIds per userId

    override fun create(userId: UUID, firstClientId: String): SsoSession {
        val now = Instant.now()
        val session = SsoSession(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            clientIds = mutableListOf(firstClientId),
            createdAt = now,
            lastActivityAt = now,
            expiresAt = now.plusSeconds(ttlSeconds),
        )
        save(session)
        redis.opsForSet().add("$userIndex$userId", session.sessionId)
        redis.expire("$userIndex$userId", Duration.ofSeconds(ttlSeconds))
        return session
    }

    override fun get(sessionId: String): SsoSession? {
        val json = redis.opsForValue().get("$prefix$sessionId") ?: return null
        return objectMapper.readValue(json, SsoSession::class.java)
    }

    override fun addClient(sessionId: String, clientId: String) {
        val session = get(sessionId) ?: return
        if (clientId !in session.clientIds) session.clientIds.add(clientId)
        save(session.copy(lastActivityAt = Instant.now()))
    }

    override fun updateActivity(sessionId: String) {
        val session = get(sessionId) ?: return
        save(session.copy(lastActivityAt = Instant.now()))
    }

    override fun delete(sessionId: String) {
        val session = get(sessionId)
        redis.delete("$prefix$sessionId")
        if (session != null) {
            redis.opsForSet().remove("$userIndex${session.userId}", sessionId)
        }
    }

    override fun deleteAllForUser(userId: UUID) {
        val sessionIds = redis.opsForSet().members("$userIndex$userId") ?: return
        sessionIds.forEach { redis.delete("$prefix$it") }
        redis.delete("$userIndex$userId")
    }

    private fun save(session: SsoSession) {
        val json = objectMapper.writeValueAsString(session)
        val remaining = Duration.between(Instant.now(), session.expiresAt)
        if (!remaining.isNegative) {
            redis.opsForValue().set("$prefix${session.sessionId}", json, remaining)
        }
    }
}
```

- [ ] **Step 4.7: Wire `ttlSeconds` via AppProperties**

`RedisSsoSessionService` needs `ttlSeconds`. Provide it via a `@Bean` in a config or inject `AppProperties` directly. Simplest: inject `AppProperties` instead of raw `Long`:
```kotlin
// Change constructor:
class RedisSsoSessionService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val props: AppProperties,
) : SsoSessionService {
    private val ttlSeconds get() = props.ssoSession.ttlSeconds
    // rest unchanged
```
Update test accordingly (mock AppProperties or pass props with defaults).

- [ ] **Step 4.8: Run tests**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.*"
```
Expected: PASS

- [ ] **Step 4.9: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/ \
        services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/
git commit -m "feat(auth-service): add AuthRequestService and SsoSessionService (Redis impl)"
```

---

## Task 5: AuthorizationController

**Files:**
- Create: `src/main/kotlin/.../controller/AuthorizationController.kt`
- Create: `src/test/kotlin/.../controller/AuthorizationControllerTest.kt`

- [ ] **Step 5.1: Write failing tests**

Create `AuthorizationControllerTest.kt`:
```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.service.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(AuthorizationController::class)
class AuthorizationControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var authRequestService: AuthRequestService
    @MockitoBean lateinit var ssoSessionService: SsoSessionService
    @MockitoBean lateinit var authCodeStore: AuthorizationCodeStore
    @MockitoBean lateinit var userServiceClient: UserServiceClient
    @MockitoBean lateinit var authEventPublisher: AuthEventPublisher

    @Test
    fun `GET authorize missing client_id returns 400`() {
        mockMvc.get("/oauth2/authorize") {
            param("response_type", "code")
            param("redirect_uri", "https://app.example.com/cb")
            param("code_challenge", "abc")
            param("code_challenge_method", "S256")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET authorize with valid params and no SSO session redirects to login page`() {
        whenever(authRequestService).thenAnswer { }  // save is void
        // ClientService lookup mocked via @MockitoBean
        // This test verifies 302 redirect — more detailed setup in integration test
        // For unit test: verify the redirect location contains login page URL
        // Requires ClientService mock — add as @MockitoBean when ClientService exists
    }

    @Test
    fun `POST callback with missing auth_request_id returns 400`() {
        whenever(authRequestService.getAndDelete("missing")).thenReturn(null)
        mockMvc.post("/oauth2/authorize/callback") {
            param("auth_request_id", "missing")
            param("username", "user@example.com")
            param("password", "secret")
        }.andExpect { status { isBadRequest() } }
    }
}
```

- [ ] **Step 5.2: Run tests to verify they fail**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.AuthorizationControllerTest"
```
Expected: FAIL — `AuthorizationController` not found

- [ ] **Step 5.3: Create AuthorizationController.kt**

```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.repository.AuthorizationCodeData
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.iamsso.services.authservice.service.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.view.RedirectView
import java.util.UUID
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@RestController
class AuthorizationController(
    private val clientRepository: OAuthClientRepository,
    private val authRequestService: AuthRequestService,
    private val ssoSessionService: SsoSessionService,
    private val authCodeStore: AuthorizationCodeStore,
    private val userServiceClient: UserServiceClient,
    private val authEventPublisher: AuthEventPublisher,
    private val props: AppProperties,
) {

    @GetMapping("/oauth2/authorize")
    fun authorize(
        @RequestParam("response_type") responseType: String?,
        @RequestParam("client_id") clientId: String?,
        @RequestParam("redirect_uri") redirectUri: String?,
        @RequestParam("scope") scope: String?,
        @RequestParam("state") state: String?,
        @RequestParam("code_challenge") codeChallenge: String?,
        @RequestParam("code_challenge_method") codeChallengeMethod: String?,
        @RequestParam("nonce") nonce: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        // Step 1: Validate client_id and redirect_uri — errors without redirect
        if (clientId == null) return errorResponse(null, "invalid_request", state)
        val client = clientRepository.findById(clientId).orElse(null)
            ?: return errorNoRedirect(response, "unknown client_id")
        if (redirectUri == null || redirectUri !in client.redirectUriList())
            return errorNoRedirect(response, "invalid redirect_uri")

        // Step 2: Validate other params
        if (responseType != "code") return errorRedirect(redirectUri, "unsupported_response_type", state)
        if (codeChallenge == null || codeChallengeMethod != "S256")
            return errorRedirect(redirectUri, "invalid_request", state)

        // Step 3: Validate scope
        val requestedScopes = scope?.split(" ") ?: emptyList()
        if (requestedScopes.any { it !in client.scopeList() })
            return errorRedirect(redirectUri, "invalid_scope", state)

        // Step 4: Check SSO session
        val sessionId = request.cookies?.find { it.name == "SSO_SESSION" }?.value
        if (sessionId != null) {
            val session = ssoSessionService.get(sessionId)
            if (session != null) {
                // Silent SSO
                ssoSessionService.updateActivity(sessionId)
                ssoSessionService.addClient(sessionId, clientId)
                val code = UUID.randomUUID().toString()
                authCodeStore.save(
                    AuthorizationCodeData(
                        code = code,
                        clientId = clientId,
                        userId = session.userId,
                        redirectUri = redirectUri,
                        scopes = requestedScopes,
                        codeChallenge = codeChallenge,
                        codeChallengeMethod = codeChallengeMethod,
                        nonce = nonce,
                        sessionId = sessionId,
                    )
                )
                val location = buildString {
                    append("$redirectUri?code=$code")
                    if (state != null) append("&state=$state")
                }
                return RedirectView(location)
            }
        }

        // Step 5: Store auth request and redirect to login page
        val authRequest = AuthRequest(
            authRequestId = UUID.randomUUID().toString(),
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            nonce = nonce,
        )
        authRequestService.save(authRequest, props.authorizationRequest.ttlSeconds)
        return RedirectView("${props.loginPage.url}/login?auth_request_id=${authRequest.authRequestId}")
    }

    @PostMapping("/oauth2/authorize/callback", consumes = ["application/x-www-form-urlencoded"])
    fun callback(
        @RequestParam("auth_request_id") authRequestId: String,
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        val loginPage = props.loginPage.url

        // Step 1: Load auth request
        val authRequest = authRequestService.getAndDelete(authRequestId)
            ?: return RedirectView("$loginPage/login?error=invalid_request")

        // Step 2: Find user
        val user = (if ("@" in username) userServiceClient.getByEmail(username)
                   else userServiceClient.getByUsername(username))
            ?: return run {
                val newId = resaveRequest(authRequest)
                authEventPublisher.publishLoginFailed(username, authRequest.clientId, "invalid_credentials")
                RedirectView("$loginPage/login?auth_request_id=$newId&error=invalid_credentials")
            }

        // Step 3: Check status BEFORE verifying credentials
        if (user.status != "ACTIVE") {
            val newId = resaveRequest(authRequest)
            return RedirectView("$loginPage/login?auth_request_id=$newId&error=account_disabled")
        }

        // Step 4: Verify credentials
        val creds = userServiceClient.verifyCredentials(user.id, password)
        if (creds == null || !creds.valid) {
            val newId = resaveRequest(authRequest)
            val reason = if (creds?.lockedUntil != null) "account_locked" else "invalid_credentials"
            authEventPublisher.publishLoginFailed(username, authRequest.clientId, reason)
            return RedirectView("$loginPage/login?auth_request_id=$newId&error=$reason")
        }

        // Step 5: Create SSO session
        val session = ssoSessionService.create(user.id, authRequest.clientId)
        val cookie = Cookie("SSO_SESSION", session.sessionId).apply {
            isHttpOnly = true
            path = "/"
            maxAge = props.ssoSession.ttlSeconds.toInt()
        }
        response.addCookie(cookie)

        // Step 6: Create auth code
        val code = UUID.randomUUID().toString()
        authCodeStore.save(
            AuthorizationCodeData(
                code = code,
                clientId = authRequest.clientId,
                userId = user.id,
                redirectUri = authRequest.redirectUri,
                scopes = authRequest.scope?.split(" ") ?: emptyList(),
                codeChallenge = authRequest.codeChallenge,
                codeChallengeMethod = authRequest.codeChallengeMethod,
                nonce = authRequest.nonce,
                sessionId = session.sessionId,
            )
        )

        // Steps 7-8: Publish events
        authEventPublisher.publishLoginSuccess(user.id, authRequest.clientId, session.sessionId)
        authEventPublisher.publishSessionCreated(session.sessionId, user.id, authRequest.clientId)

        // Step 9: Redirect
        val location = buildString {
            append("${authRequest.redirectUri}?code=$code")
            if (authRequest.state != null) append("&state=${authRequest.state}")
        }
        return RedirectView(location)
    }

    private fun resaveRequest(req: AuthRequest): String {
        val newReq = req.copy(authRequestId = UUID.randomUUID().toString())
        authRequestService.save(newReq, props.authorizationRequest.ttlSeconds)
        return newReq.authRequestId
    }

    private fun errorNoRedirect(response: HttpServletResponse, message: String): RedirectView {
        response.sendError(HttpStatus.BAD_REQUEST.value(), message)
        return RedirectView("")
    }

    private fun errorRedirect(redirectUri: String, error: String, state: String?): RedirectView {
        val location = buildString {
            append("$redirectUri?error=$error")
            if (state != null) append("&state=$state")
        }
        return RedirectView(location)
    }

    private fun errorResponse(redirectUri: String?, error: String, state: String?): RedirectView {
        return if (redirectUri != null) errorRedirect(redirectUri, error, state)
        else RedirectView("?error=$error")
    }
}
```

- [ ] **Step 5.4: Run tests**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.AuthorizationControllerTest"
```
Expected: PASS (the basic tests)

- [ ] **Step 5.5: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/AuthorizationController.kt \
        services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/AuthorizationControllerTest.kt
git commit -m "feat(auth-service): add AuthorizationController with SSO session support"
```

---

## Task 6: AuthEventPublisher

**Files:**
- Create: `src/main/kotlin/.../service/AuthEventPublisher.kt`

This service is needed by `AuthorizationController`, `TokenController`, `RevocationController`, and `SessionController`. Implement it before the remaining controllers.

- [ ] **Step 6.1: Create AuthEventPublisher.kt**

```kotlin
package org.iamsso.services.authservice.service

import org.iamsso.contracts.events.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthEventPublisher(
    private val kafka: KafkaTemplate<String, Any>,
) {
    fun publishLoginSuccess(userId: UUID, clientId: String, sessionId: String) =
        kafka.send(KafkaTopics.AUTH_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "auth.login-success",
                data = LoginSuccessEvent(userId = userId, clientId = clientId, sessionId = sessionId)))

    fun publishLoginFailed(identifier: String, clientId: String, reason: String) =
        kafka.send(KafkaTopics.AUTH_EVENTS, clientId,
            CloudEventEnvelope(source = "auth-service", type = "auth.login-failed",
                data = LoginFailedEvent(identifier = identifier, clientId = clientId, reason = reason)))

    fun publishTokenIssued(userId: UUID, clientId: String, grantType: String, scopes: List<String>, sessionId: String?) =
        kafka.send(KafkaTopics.AUTH_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "auth.token-issued",
                data = TokenIssuedEvent(userId = userId, clientId = clientId, grantType = grantType, scopes = scopes, sessionId = sessionId)))

    fun publishTokenRevoked(userId: UUID?, clientId: String, tokenType: String, reason: String) =
        kafka.send(KafkaTopics.AUTH_EVENTS, clientId,
            CloudEventEnvelope(source = "auth-service", type = "auth.token-revoked",
                data = TokenRevokedEvent(userId = userId, clientId = clientId, tokenType = tokenType, reason = reason)))

    fun publishSessionCreated(sessionId: String, userId: UUID, clientId: String) =
        kafka.send(KafkaTopics.SESSION_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "session.created",
                data = SessionCreatedEvent(sessionId = sessionId, userId = userId, clientId = clientId)))

    fun publishSessionDestroyed(sessionId: String, userId: UUID, reason: String) =
        kafka.send(KafkaTopics.SESSION_EVENTS, userId.toString(),
            CloudEventEnvelope(source = "auth-service", type = "session.destroyed",
                data = SessionDestroyedEvent(sessionId = sessionId, userId = userId, reason = reason)))
}
```

- [ ] **Step 6.2: Compile**

```bash
./gradlew :services:auth-service:compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6.3: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/AuthEventPublisher.kt
git commit -m "feat(auth-service): add AuthEventPublisher for Kafka auth/session events"
```

---

## Task 7: UserInfoController

**Files:**
- Create: `src/main/kotlin/.../controller/UserInfoController.kt`
- Create: `src/test/kotlin/.../controller/UserInfoControllerTest.kt`

- [ ] **Step 7.1: Write failing tests**

Create `UserInfoControllerTest.kt`:
```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.JwtKeyProvider
import org.iamsso.services.authservice.service.UserServiceClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(UserInfoController::class)
class UserInfoControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var userServiceClient: UserServiceClient
    @MockitoBean lateinit var jwtKeyProvider: JwtKeyProvider

    @Test
    fun `GET userinfo without token returns 401`() {
        mockMvc.get("/userinfo")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `POST userinfo without token returns 401`() {
        mockMvc.post("/userinfo") {}
            .andExpect { status { isUnauthorized() } }
    }
}
```

- [ ] **Step 7.2: Run tests to verify they fail**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.UserInfoControllerTest"
```
Expected: FAIL

- [ ] **Step 7.3: Create UserInfoController.kt**

```kotlin
package org.iamsso.services.authservice.controller

import com.nimbusds.jwt.SignedJWT
import org.iamsso.contracts.auth.model.UserInfoResponse
import org.iamsso.services.authservice.config.JwtKeyProvider
import org.iamsso.services.authservice.service.UserServiceClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
class UserInfoController(
    private val jwtKeyProvider: JwtKeyProvider,
    private val userServiceClient: UserServiceClient,
) {

    @GetMapping("/userinfo")
    fun getUserInfo(@RequestHeader("Authorization", required = false) authHeader: String?): UserInfoResponse =
        handleUserInfo(authHeader)

    @PostMapping("/userinfo")
    fun postUserInfo(@RequestHeader("Authorization", required = false) authHeader: String?): UserInfoResponse =
        handleUserInfo(authHeader)

    private fun handleUserInfo(authHeader: String?): UserInfoResponse {
        val token = authHeader?.removePrefix("Bearer ")?.trim()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token")

        val jwt = try {
            val signed = SignedJWT.parse(token)
            val verifier = com.nimbusds.jose.crypto.RSASSAVerifier(jwtKeyProvider.publicKey)
            if (!signed.verify(verifier)) throw IllegalArgumentException("invalid signature")
            signed.jwtClaimsSet
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        val sub = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing sub")
        val scope = jwt.getStringClaim("scope") ?: ""
        val scopes = scope.split(" ")

        val response = UserInfoResponse().apply { this.sub = sub }

        if ("email" in scopes) {
            response.email = jwt.getStringClaim("email")
            response.emailVerified = jwt.getBooleanClaim("email_verified")
        }

        if ("profile" in scopes) {
            val profile = userServiceClient.getProfile(UUID.fromString(sub))
            response.preferredUsername = profile?.displayName
            response.name = listOfNotNull(profile?.firstName, profile?.lastName).joinToString(" ").ifBlank { null }
            response.givenName = profile?.firstName
            response.familyName = profile?.lastName
            response.locale = profile?.locale
            response.picture = profile?.avatarUrl
        }

        return response
    }
}
```

- [ ] **Step 7.4: Run tests**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.UserInfoControllerTest"
```
Expected: PASS

- [ ] **Step 7.5: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/UserInfoController.kt \
        services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/UserInfoControllerTest.kt
git commit -m "feat(auth-service): add UserInfoController (GET+POST /userinfo, scope-based claims)"
```

---

## Task 8: ClientController

**Files:**
- Create: `src/main/kotlin/.../controller/ClientController.kt`
- Create: `src/test/kotlin/.../controller/ClientControllerTest.kt`

`ClientController` delegates to the existing `ClientService`. No new service logic needed.

- [ ] **Step 8.1: Write failing tests**

Create `ClientControllerTest.kt`:
```kotlin
package org.iamsso.services.authservice.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ClientController::class)
class ClientControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    // ClientService mock added when ClientService class exists

    @Test
    fun `GET clients without token returns 401`() {
        mockMvc.get("/api/v1/clients")
            .andExpect { status { isUnauthorized() } }
    }
}
```

- [ ] **Step 8.2: Run tests to verify they fail**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.ClientControllerTest"
```
Expected: FAIL

- [ ] **Step 8.3: Create ClientController.kt**

```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.contracts.auth.model.*
import org.iamsso.services.authservice.service.ClientService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/clients")
class ClientController(private val clientService: ClientService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterClientRequest): ClientWithSecretResponse =
        clientService.register(request)

    @GetMapping
    fun list(): List<ClientResponse> = clientService.findAll()

    @GetMapping("/{clientId}")
    fun get(@PathVariable clientId: String): ClientResponse =
        clientService.findById(clientId)

    @PatchMapping("/{clientId}")
    fun update(@PathVariable clientId: String, @RequestBody request: UpdateClientRequest): ClientResponse =
        clientService.update(clientId, request)

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable clientId: String) = clientService.delete(clientId)

    @PostMapping("/{clientId}/secret/rotate")
    fun rotateSecret(@PathVariable clientId: String): ClientWithSecretResponse =
        clientService.rotateSecret(clientId)
}
```

- [ ] **Step 8.4: Run tests**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.ClientControllerTest"
```
Expected: PASS

- [ ] **Step 8.5: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/ClientController.kt \
        services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/ClientControllerTest.kt
git commit -m "feat(auth-service): add ClientController for OAuth client CRUD"
```

---

## Task 9: SessionController

**Files:**
- Create: `src/main/kotlin/.../controller/SessionController.kt`
- Create: `src/test/kotlin/.../controller/SessionControllerTest.kt`

- [ ] **Step 9.1: Write failing tests**

Create `SessionControllerTest.kt`:
```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.SsoSessionService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(SessionController::class)
class SessionControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockitoBean lateinit var ssoSessionService: SsoSessionService
    @MockitoBean lateinit var refreshTokenRepository: RefreshTokenRepository
    @MockitoBean lateinit var authEventPublisher: AuthEventPublisher

    @Test
    fun `GET current without SSO_SESSION cookie returns 401`() {
        whenever(ssoSessionService.get(org.mockito.kotlin.any())).thenReturn(null)
        mockMvc.get("/api/v1/sessions/current")
            .andExpect { status { isUnauthorized() } }
    }
}
```

- [ ] **Step 9.2: Run tests to verify they fail**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.SessionControllerTest"
```
Expected: FAIL

- [ ] **Step 9.3: Create SessionController.kt**

```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.contracts.auth.model.SessionResponse
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.OAuthClientRepository
import org.iamsso.services.authservice.service.SsoSessionService
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController(
    private val ssoSessionService: SsoSessionService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authEventPublisher: AuthEventPublisher,
) {

    @GetMapping("/current")
    fun getCurrent(request: HttpServletRequest): SessionResponse {
        val session = resolveSession(request)
        return SessionResponse().apply {
            sessionId = session.sessionId
            userId = session.userId
            clientIds = session.clientIds
            createdAt = OffsetDateTime.ofInstant(session.createdAt, ZoneOffset.UTC)
            lastActivityAt = OffsetDateTime.ofInstant(session.lastActivityAt, ZoneOffset.UTC)
            expiresAt = OffsetDateTime.ofInstant(session.expiresAt, ZoneOffset.UTC)
        }
    }

    @PostMapping("/logout", consumes = ["application/x-www-form-urlencoded", "application/json", "*/*"])
    @Transactional
    fun logout(
        @RequestParam("post_logout_redirect_uri", required = false) postLogoutUri: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val session = resolveSession(request)
        refreshTokenRepository.revokeAllBySessionId(UUID.fromString(session.sessionId))
        ssoSessionService.delete(session.sessionId)
        authEventPublisher.publishSessionDestroyed(session.sessionId, session.userId, "logout")
        clearCookie(response)
        if (postLogoutUri != null) {
            response.sendRedirect(postLogoutUri)
        }
    }

    @PostMapping("/logout-all")
    @Transactional
    fun logoutAll(request: HttpServletRequest, response: HttpServletResponse) {
        val session = resolveSession(request)
        refreshTokenRepository.revokeAllByUserId(session.userId)
        ssoSessionService.deleteAllForUser(session.userId)
        authEventPublisher.publishSessionDestroyed(session.sessionId, session.userId, "logout-all")
        clearCookie(response)
    }

    private fun resolveSession(request: HttpServletRequest) =
        request.cookies?.find { it.name == "SSO_SESSION" }?.value
            ?.let { ssoSessionService.get(it) }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No active session")

    private fun clearCookie(response: HttpServletResponse) {
        response.addCookie(Cookie("SSO_SESSION", "").apply {
            isHttpOnly = true
            path = "/"
            maxAge = 0
        })
    }
}
```

- [ ] **Step 9.4: Run tests**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.SessionControllerTest"
```
Expected: PASS

- [ ] **Step 9.5: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/SessionController.kt \
        services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/SessionControllerTest.kt
git commit -m "feat(auth-service): add SessionController (current, logout, logout-all)"
```

---

## Task 10: UserKafkaConsumer

**Files:**
- Create: `src/main/kotlin/.../consumer/UserKafkaConsumer.kt`
- Create: `src/test/kotlin/.../consumer/UserKafkaConsumerTest.kt`

- [ ] **Step 10.1: Write failing tests**

Create `src/test/kotlin/.../consumer/UserKafkaConsumerTest.kt`:
```kotlin
package org.iamsso.services.authservice.consumer

import org.iamsso.contracts.events.*
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.SsoSessionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Instant
import java.util.UUID

class UserKafkaConsumerTest {

    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var ssoSessionService: SsoSessionService
    private lateinit var consumer: UserKafkaConsumer

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        consumer = UserKafkaConsumer(refreshTokenRepository, ssoSessionService)
    }

    @Test
    fun `user deleted event revokes tokens and sessions`() {
        val userId = UUID.randomUUID()
        val event = CloudEventEnvelope(
            source = "user-service",
            type = "user.deleted",
            data = UserDeletedEvent(userId = userId, timestamp = Instant.now()),
        )
        consumer.onUserEvent(event)
        verify(refreshTokenRepository).revokeAllByUserId(userId)
        verify(ssoSessionService).deleteAllForUser(userId)
    }

    @Test
    fun `user status changed to ACTIVE does nothing`() {
        val userId = UUID.randomUUID()
        val event = CloudEventEnvelope(
            source = "user-service",
            type = "user.status-changed",
            data = UserStatusChangedEvent(
                userId = userId,
                oldStatus = "SUSPENDED",
                newStatus = "ACTIVE",
                reason = "admin",
                timestamp = Instant.now(),
            ),
        )
        consumer.onUserEvent(event)
        verifyNoInteractions(refreshTokenRepository)
        verifyNoInteractions(ssoSessionService)
    }

    @Test
    fun `user status changed to SUSPENDED revokes tokens`() {
        val userId = UUID.randomUUID()
        val event = CloudEventEnvelope(
            source = "user-service",
            type = "user.status-changed",
            data = UserStatusChangedEvent(
                userId = userId,
                oldStatus = "ACTIVE",
                newStatus = "SUSPENDED",
                reason = "admin",
                timestamp = Instant.now(),
            ),
        )
        consumer.onUserEvent(event)
        verify(refreshTokenRepository).revokeAllByUserId(userId)
        verify(ssoSessionService).deleteAllForUser(userId)
    }
}
```

- [ ] **Step 10.2: Run tests to verify they fail**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.consumer.UserKafkaConsumerTest"
```
Expected: FAIL

- [ ] **Step 10.3: Create UserKafkaConsumer.kt**

```kotlin
package org.iamsso.services.authservice.consumer

import org.iamsso.contracts.events.*
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.SsoSessionService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserKafkaConsumer(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val ssoSessionService: SsoSessionService,
) {
    @KafkaListener(topics = [KafkaTopics.USER_EVENTS], groupId = "auth-service")
    @Transactional
    fun onUserEvent(envelope: CloudEventEnvelope<DomainEvent>) {
        when (val event = envelope.data) {
            is UserDeletedEvent -> {
                refreshTokenRepository.revokeAllByUserId(event.userId)
                ssoSessionService.deleteAllForUser(event.userId)
            }
            is UserStatusChangedEvent -> {
                if (event.newStatus != "ACTIVE") {
                    refreshTokenRepository.revokeAllByUserId(event.userId)
                    ssoSessionService.deleteAllForUser(event.userId)
                }
            }
            else -> Unit
        }
    }
}
```

- [ ] **Step 10.4: Run tests**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.consumer.UserKafkaConsumerTest"
```
Expected: PASS

- [ ] **Step 10.5: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/consumer/ \
        services/auth-service/src/test/kotlin/org/iamsso/services/authservice/consumer/
git commit -m "feat(auth-service): add UserKafkaConsumer (revoke tokens on user.deleted/status-changed)"
```

---

## Task 11: Full build verification

- [ ] **Step 11.1: Run all tests**

```bash
./gradlew :libs:contracts:test :services:auth-service:test
```
Expected: All PASS

- [ ] **Step 11.2: Verify compilation of entire project**

```bash
./gradlew build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 11.3: Final commit if needed**

```bash
git status  # should be clean
```

---

## Notes for the implementer

- **`ClientService`** is referenced in `ClientController` but not yet in the filesystem. It's part of the existing ~80% and assumed to exist. If it's missing, `ClientController` will fail to compile — check for it or stub it.
- **`UserDeletedEvent` / `UserStatusChangedEvent`** from contracts: verify their fields (`userId: UUID`, `newStatus: String`) match what's used in `UserKafkaConsumer`. Check `UserEvents.kt` in `libs/contracts`.
- **`UserInfoResponse`** from generated contracts: the field names (`sub`, `email`, `preferredUsername`, etc.) are generated by OpenAPI. Check the actual generated class in `libs/contracts/build/generated/auth-service/` if setter names differ.
- **`SessionResponse`** from generated contracts: same note as above — verify actual field names.
- **Test runner note:** `@MockitoBean` is Spring Boot 3.4+ annotation. If unavailable, use `@MockBean` from `spring-boot-test`.
