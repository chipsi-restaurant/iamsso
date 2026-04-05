# TokenService + Token Endpoints — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement TokenService with grant handler pattern, TokenController (authorization_code/refresh_token/client_credentials/device_code), RevocationController, IntrospectionController, DeviceController, DiscoveryController, and JwksController.

**Architecture:** Grant Handler Pattern — TokenService routes to AuthCodeGrantHandler / RefreshTokenGrantHandler / ClientCredentialsGrantHandler / DeviceCodeGrantHandler. JwtIssuer signs access tokens and OIDC id_tokens via Nimbus. TokenFamilyService tracks refresh token chains in Redis for reuse detection. Four controllers (Token, Revocation, Introspection, Device) authenticate clients via `TokenService.authenticateClient()`.

**Tech Stack:** Kotlin 2.1+, Spring Boot 4, Nimbus JOSE+JWT 10.0.2, Redis (StringRedisTemplate), Kafka (KafkaTemplate), JUnit 5, Mockito (mockito-kotlin), MockMvc, H2 in tests.

---

## Path Shorthand

`src/main/kotlin/org/iamsso/services/authservice/` → abbreviated as `.../authservice/`
`src/test/kotlin/org/iamsso/services/authservice/` → abbreviated as `test/.../authservice/`

---

## File Map

### Task 0 — Commit untracked baseline
No new files — commit existing untracked files.

### Task 1 — Prerequisites
- Modify: `.../authservice/entity/RefreshTokenEntity.kt` — add `familyId: UUID?`, make `sessionId: UUID?` nullable
- Modify: `.../authservice/repository/RefreshTokenRepository.kt` — add `revokeByTokenHash`
- Modify: `.../authservice/config/AppConfig.kt` — add `TokensProps`, `tokens` field
- Modify: `src/main/resources/application.yaml` — add `app.tokens.*`
- Create: `src/main/resources/db/changelog/changelogs/002-add-family-id.yaml`

### Task 2 — JwtIssuer
- Create: `.../authservice/service/JwtIssuer.kt`
- Create: `test/.../authservice/service/JwtIssuerTest.kt`

### Task 3 — TokenFamilyService
- Create: `.../authservice/service/TokenFamilyService.kt`
- Create: `test/.../authservice/service/TokenFamilyServiceTest.kt`

### Task 4 — GrantHandler interface + TokenResponse
- Create: `.../authservice/service/GrantHandler.kt`

### Task 5 — AuthCodeGrantHandler
- Create: `.../authservice/service/grant/AuthCodeGrantHandler.kt`
- Create: `test/.../authservice/service/grant/AuthCodeGrantHandlerTest.kt`

### Task 6 — RefreshTokenGrantHandler
- Create: `.../authservice/service/grant/RefreshTokenGrantHandler.kt`
- Create: `test/.../authservice/service/grant/RefreshTokenGrantHandlerTest.kt`

### Task 7 — ClientCredentialsGrantHandler
- Create: `.../authservice/service/grant/ClientCredentialsGrantHandler.kt`
- Create: `test/.../authservice/service/grant/ClientCredentialsGrantHandlerTest.kt`

### Task 8 — DeviceCodeGrantHandler
- Create: `.../authservice/service/grant/DeviceCodeGrantHandler.kt`
- Create: `test/.../authservice/service/grant/DeviceCodeGrantHandlerTest.kt`

### Task 9 — TokenService
- Create: `.../authservice/service/TokenService.kt`
- Create: `test/.../authservice/service/TokenServiceTest.kt`

### Task 10 — TokenController
- Create: `.../authservice/controller/TokenController.kt`
- Create: `test/.../authservice/controller/TokenControllerTest.kt`

### Task 11 — RevocationController
- Create: `.../authservice/controller/RevocationController.kt`
- Create: `test/.../authservice/controller/RevocationControllerTest.kt`

### Task 12 — IntrospectionController
- Create: `.../authservice/controller/IntrospectionController.kt`
- Create: `test/.../authservice/controller/IntrospectionControllerTest.kt`

### Task 13 — DeviceController
- Create: `.../authservice/controller/DeviceController.kt`
- Create: `test/.../authservice/controller/DeviceControllerTest.kt`

### Task 14 — DiscoveryController + JwksController
- Create: `.../authservice/controller/DiscoveryController.kt`
- Modify: `.../authservice/controller/JwksController.kt` — fill stub
- Create: `test/.../authservice/controller/DiscoveryControllerTest.kt`

### Task 15 — SecurityConfig update + compile check
- Modify: `.../authservice/config/SecurityConfig.kt`

---

## Task 0: Commit untracked baseline files

**Files:**
- Commit: all untracked files listed in `git status`

- [ ] **Step 0.1: Verify untracked files**

```bash
git status --short services/auth-service/
```

Expected output includes `??` markers for `entity/`, `exception/`, `repository/DeviceCodeStore.kt`, `repository/OAuthClientRepository.kt`, `controller/JwksController.kt`, `src/main/resources/db/`.

- [ ] **Step 0.2: Stage and commit baseline**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/entity/ \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/exception/ \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/repository/DeviceCodeStore.kt \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/repository/OAuthClientRepository.kt \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/JwksController.kt \
  services/auth-service/src/main/resources/db/ \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/AuthServiceApplication.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/AuthServiceApplicationTests.kt \
  services/auth-service/src/test/resources/application-test.yaml
git commit -m "chore(auth-service): commit untracked baseline files (entities, exceptions, migrations)"
```

---

## Task 1: Prerequisites — DB migration, entity, repository, config

**Files:**
- Modify: `.../authservice/entity/RefreshTokenEntity.kt`
- Modify: `.../authservice/repository/RefreshTokenRepository.kt`
- Modify: `.../authservice/config/AppConfig.kt`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/resources/db/changelog/changelogs/002-add-family-id.yaml`

- [ ] **Step 1.1: Add familyId column migration**

Create `services/auth-service/src/main/resources/db/changelog/changelogs/002-add-family-id.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 002-add-family-id-to-refresh-tokens
      author: Arseniy Kolyshkin
      changes:
        - addColumn:
            schemaName: iamsso_auth
            tableName: refresh_tokens
            columns:
              - column:
                  name: family_id
                  type: uuid
        - createIndex:
            schemaName: iamsso_auth
            tableName: refresh_tokens
            indexName: idx_rt_family_id
            columns:
              - column: { name: family_id }
```

- [ ] **Step 1.2: Register migration in master changelog**

In `services/auth-service/src/main/resources/db/changelog/db.changelog-master.yaml`, add the new entry (keep existing `001` entry):

```yaml
databaseChangeLog:
  - include:
      file: classpath:db/changelog/changelogs/001-init-schema.yaml
  - include:
      file: classpath:db/changelog/changelogs/002-add-family-id.yaml
```

- [ ] **Step 1.3: Update RefreshTokenEntity — make sessionId nullable, add familyId**

Replace the full file `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/entity/RefreshTokenEntity.kt`:

```kotlin
package org.iamsso.services.authservice.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens", schema = "iamsso_auth")
class RefreshTokenEntity(
    @Id val id: UUID = UUID.randomUUID(),

    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String = "",

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Column(name = "client_id", nullable = false)
    val clientId: String = "",

    @Column(nullable = false)
    val scopes: String = "",

    @Column(name = "session_id")
    val sessionId: UUID? = null,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(name = "replaced_by")
    var replacedBy: UUID? = null,

    @Column(name = "family_id")
    var familyId: UUID? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    val isExpired: Boolean get() = expiresAt.isBefore(Instant.now())
    val isActive: Boolean get() = !revoked && !isExpired
}
```

- [ ] **Step 1.4: Add revokeByTokenHash to RefreshTokenRepository**

In `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/repository/RefreshTokenRepository.kt`, add one method:

```kotlin
package org.iamsso.services.authservice.repository

import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    fun revokeAllByUserId(userId: UUID): Int

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.clientId = :clientId AND r.revoked = false")
    fun revokeAllByClientId(clientId: String): Int

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.sessionId = :sessionId AND r.revoked = false")
    fun revokeAllBySessionId(sessionId: UUID): Int

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.tokenHash = :hash")
    fun revokeByTokenHash(hash: String): Int
}
```

- [ ] **Step 1.5: Add TokensProps to AppProperties**

In `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/config/AppConfig.kt`, add `TokensProps` and `tokens` field:

```kotlin
package org.iamsso.services.authservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val issuer: String = "http://localhost:8080",
    val jwt: JwtProps = JwtProps(),
    val tokens: TokensProps = TokensProps(),
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
    data class TokensProps(val refreshTokenTtlSeconds: Long = 2592000)
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

- [ ] **Step 1.6: Add app.tokens property to application.yaml**

Append to `services/auth-service/src/main/resources/application.yaml` (after `authorization-request` block):

```yaml
  tokens:
    refresh-token-ttl-seconds: 2592000
```

The full `app:` block should now include:
```yaml
app:
  issuer: http://localhost:8080
  jwt:
    standard:
      algorithm: RS256
      key-id: standard-key-1
    gost:
      enabled: false
      key-id: gost-key-1
  tokens:
    refresh-token-ttl-seconds: 2592000
  tokens:
    authorization-code-ttl-seconds: 60
    device-code-ttl-seconds: 600
    device-code-polling-interval: 5
  user-service:
    base-url: http://localhost:8081
  login-page:
    url: http://localhost:3000
  admin:
    scope: iam:admin
  sso-session:
    ttl-seconds: 86400
  authorization-request:
    ttl-seconds: 300
```

- [ ] **Step 1.7: Compile to verify**

```bash
./gradlew :services:auth-service:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 1.8: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/entity/RefreshTokenEntity.kt \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/repository/RefreshTokenRepository.kt \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/config/AppConfig.kt \
  services/auth-service/src/main/resources/application.yaml \
  services/auth-service/src/main/resources/db/changelog/changelogs/002-add-family-id.yaml \
  services/auth-service/src/main/resources/db/changelog/db.changelog-master.yaml
git commit -m "feat(auth-service): add familyId to RefreshTokenEntity, TokensProps, migration 002"
```

---

## Task 2: JwtIssuer

**Files:**
- Create: `.../authservice/service/JwtIssuer.kt`
- Create: `test/.../authservice/service/JwtIssuerTest.kt`

- [ ] **Step 2.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/JwtIssuerTest.kt`:

```kotlin
package org.iamsso.services.authservice.service

import com.nimbusds.jwt.SignedJWT
import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.config.JwtKeyProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtIssuerTest {

    private lateinit var jwtIssuer: JwtIssuer
    private val props = AppProperties()
    private val keyProvider = JwtKeyProvider(props)

    @BeforeEach
    fun setUp() {
        jwtIssuer = JwtIssuer(keyProvider, props)
    }

    @Test
    fun `issueAccessToken returns signed JWT with correct claims`() {
        val userId = UUID.randomUUID()
        val clientId = "test-client"
        val token = jwtIssuer.issueAccessToken(
            userId = userId,
            clientId = clientId,
            scopes = listOf("openid", "email"),
            sessionId = "session-1",
            email = "user@example.com",
            emailVerified = true,
            ttlSeconds = 3600,
        )

        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertEquals(props.issuer, claims.issuer)
        assertEquals(userId.toString(), claims.subject)
        assertEquals(clientId, claims.audience.first())
        assertEquals("openid email", claims.getStringClaim("scope"))
        assertEquals("session-1", claims.getStringClaim("sid"))
        assertEquals("user@example.com", claims.getStringClaim("email"))
        assertEquals(true, claims.getBooleanClaim("email_verified"))
    }

    @Test
    fun `issueAccessToken without email scope does not include email claims`() {
        val token = jwtIssuer.issueAccessToken(
            userId = UUID.randomUUID(),
            clientId = "client",
            scopes = listOf("openid"),
            sessionId = null,
            email = "user@example.com",
            emailVerified = true,
            ttlSeconds = 3600,
        )
        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertNull(claims.getStringClaim("email"))
        assertNull(claims.getStringClaim("sid"))
    }

    @Test
    fun `issueIdToken includes nonce and email`() {
        val userId = UUID.randomUUID()
        val token = jwtIssuer.issueIdToken(
            userId = userId,
            clientId = "client",
            nonce = "abc123",
            scopes = listOf("openid", "email"),
            ttlSeconds = 3600,
            email = "user@example.com",
        )
        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertEquals(userId.toString(), claims.subject)
        assertEquals("abc123", claims.getStringClaim("nonce"))
        assertEquals("user@example.com", claims.getStringClaim("email"))
    }

    @Test
    fun `verify returns claims for valid token`() {
        val token = jwtIssuer.issueAccessToken(
            userId = UUID.randomUUID(),
            clientId = "client",
            scopes = listOf("openid"),
            sessionId = null,
            ttlSeconds = 3600,
        )
        assertNotNull(jwtIssuer.verify(token))
    }

    @Test
    fun `verify returns null for garbage string`() {
        assertNull(jwtIssuer.verify("not.a.jwt"))
    }
}
```

- [ ] **Step 2.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.JwtIssuerTest"
```

Expected: `FAILED` — `JwtIssuer` not found.

- [ ] **Step 2.3: Implement JwtIssuer**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/JwtIssuer.kt`:

```kotlin
package org.iamsso.services.authservice.service

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.config.JwtKeyProvider
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID

@Component
class JwtIssuer(
    private val keyProvider: JwtKeyProvider,
    private val props: AppProperties,
) {
    private val signer = RSASSASigner(keyProvider.privateKey)
    private val verifier = RSASSAVerifier(keyProvider.publicKey)

    fun issueAccessToken(
        userId: UUID?,
        clientId: String,
        scopes: List<String>,
        sessionId: String?,
        email: String? = null,
        emailVerified: Boolean? = null,
        ttlSeconds: Long,
    ): String {
        val now = Instant.now()
        val builder = JWTClaimsSet.Builder()
            .issuer(props.issuer)
            .subject(userId?.toString() ?: clientId)
            .audience(clientId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
            .jwtID(UUID.randomUUID().toString())
            .claim("scope", scopes.joinToString(" "))

        sessionId?.let { builder.claim("sid", it) }
        if ("email" in scopes && email != null) {
            builder.claim("email", email)
            builder.claim("email_verified", emailVerified ?: false)
        }

        return sign(builder.build())
    }

    fun issueIdToken(
        userId: UUID,
        clientId: String,
        nonce: String?,
        scopes: List<String>,
        ttlSeconds: Long,
        email: String? = null,
        displayName: String? = null,
        preferredUsername: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        locale: String? = null,
    ): String {
        val now = Instant.now()
        val builder = JWTClaimsSet.Builder()
            .issuer(props.issuer)
            .subject(userId.toString())
            .audience(clientId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
            .jwtID(UUID.randomUUID().toString())

        nonce?.let { builder.claim("nonce", it) }
        if ("email" in scopes && email != null) builder.claim("email", email)
        if ("profile" in scopes) {
            displayName?.let { builder.claim("name", it) }
            preferredUsername?.let { builder.claim("preferred_username", it) }
            firstName?.let { builder.claim("given_name", it) }
            lastName?.let { builder.claim("family_name", it) }
            locale?.let { builder.claim("locale", it) }
        }

        return sign(builder.build())
    }

    fun verify(token: String): JWTClaimsSet? = try {
        val jwt = SignedJWT.parse(token)
        if (!jwt.verify(verifier)) return null
        val claims = jwt.jwtClaimsSet
        if (claims.expirationTime.before(Date())) return null
        claims
    } catch (_: Exception) {
        null
    }

    private fun sign(claims: JWTClaimsSet): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(props.jwt.keyId)
            .build()
        val jwt = SignedJWT(header, claims)
        jwt.sign(signer)
        return jwt.serialize()
    }
}
```

- [ ] **Step 2.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.JwtIssuerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/JwtIssuer.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/JwtIssuerTest.kt
git commit -m "feat(auth-service): add JwtIssuer (access token + id_token signing)"
```

---

## Task 3: TokenFamilyService

**Files:**
- Create: `.../authservice/service/TokenFamilyService.kt`
- Create: `test/.../authservice/service/TokenFamilyServiceTest.kt`

- [ ] **Step 3.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/TokenFamilyServiceTest.kt`:

```kotlin
package org.iamsso.services.authservice.service

import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TokenFamilyServiceTest {

    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var setOps: SetOperations<String, String>
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var service: RedisTokenFamilyService

    @BeforeEach
    fun setUp() {
        whenever(redis.opsForSet()).thenReturn(setOps)
        service = RedisTokenFamilyService(redis, refreshTokenRepository)
    }

    @Test
    fun `initFamily adds hash to set and sets expiry`() {
        val familyId = UUID.randomUUID()
        service.initFamily(familyId, "hash1", Duration.ofSeconds(60))
        verify(setOps).add("auth:family:$familyId", "hash1")
        verify(redis).expire("auth:family:$familyId", Duration.ofSeconds(60))
    }

    @Test
    fun `addToFamily adds hash to existing set`() {
        val familyId = UUID.randomUUID()
        service.addToFamily(familyId, "hash2")
        verify(setOps).add("auth:family:$familyId", "hash2")
    }

    @Test
    fun `revokeFamily calls revokeByTokenHash for each member and deletes key`() {
        val familyId = UUID.randomUUID()
        val key = "auth:family:$familyId"
        whenever(setOps.members(key)).thenReturn(setOf("hash1", "hash2"))
        service.revokeFamily(familyId)
        verify(refreshTokenRepository).revokeByTokenHash("hash1")
        verify(refreshTokenRepository).revokeByTokenHash("hash2")
        verify(redis).delete(key)
    }

    @Test
    fun `revokeFamily does nothing when family not in Redis`() {
        val familyId = UUID.randomUUID()
        whenever(setOps.members("auth:family:$familyId")).thenReturn(null)
        service.revokeFamily(familyId)
        verify(refreshTokenRepository, org.mockito.kotlin.never()).revokeByTokenHash(org.mockito.kotlin.any())
    }
}
```

- [ ] **Step 3.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.TokenFamilyServiceTest"
```

Expected: `FAILED` — `RedisTokenFamilyService` not found.

- [ ] **Step 3.3: Implement TokenFamilyService**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/TokenFamilyService.kt`:

```kotlin
package org.iamsso.services.authservice.service

import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

interface TokenFamilyService {
    fun initFamily(familyId: UUID, tokenHash: String, ttl: Duration)
    fun addToFamily(familyId: UUID, tokenHash: String)
    fun revokeFamily(familyId: UUID)
}

@Service
class RedisTokenFamilyService(
    private val redis: StringRedisTemplate,
    private val refreshTokenRepository: RefreshTokenRepository,
) : TokenFamilyService {

    private val prefix = "auth:family:"

    override fun initFamily(familyId: UUID, tokenHash: String, ttl: Duration) {
        val key = "$prefix$familyId"
        redis.opsForSet().add(key, tokenHash)
        redis.expire(key, ttl)
    }

    override fun addToFamily(familyId: UUID, tokenHash: String) {
        redis.opsForSet().add("$prefix$familyId", tokenHash)
    }

    override fun revokeFamily(familyId: UUID) {
        val key = "$prefix$familyId"
        val hashes = redis.opsForSet().members(key) ?: return
        hashes.forEach { refreshTokenRepository.revokeByTokenHash(it) }
        redis.delete(key)
    }
}
```

- [ ] **Step 3.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.TokenFamilyServiceTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/TokenFamilyService.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/TokenFamilyServiceTest.kt
git commit -m "feat(auth-service): add TokenFamilyService (refresh token reuse detection)"
```

---

## Task 4: GrantHandler interface + TokenResponse

**Files:**
- Create: `.../authservice/service/GrantHandler.kt`

- [ ] **Step 4.1: Create GrantHandler.kt**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/GrantHandler.kt`:

```kotlin
package org.iamsso.services.authservice.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.iamsso.services.authservice.entity.OAuthClientEntity

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String = "Bearer",
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("id_token") val idToken: String? = null,
    val scope: String,
)

interface GrantHandler {
    fun supports(grantType: String): Boolean
    fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse
}
```

- [ ] **Step 4.2: Compile to verify**

```bash
./gradlew :services:auth-service:compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.3: Commit**

```bash
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/GrantHandler.kt
git commit -m "feat(auth-service): add GrantHandler interface and TokenResponse"
```

---

## Task 5: AuthCodeGrantHandler

**Files:**
- Create: `.../authservice/service/grant/AuthCodeGrantHandler.kt`
- Create: `test/.../authservice/service/grant/AuthCodeGrantHandlerTest.kt`

- [ ] **Step 5.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/AuthCodeGrantHandlerTest.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.AuthorizationCodeData
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.UserServiceClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class AuthCodeGrantHandlerTest {

    @Mock lateinit var authCodeStore: AuthorizationCodeStore
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var tokenFamilyService: TokenFamilyService
    @Mock lateinit var authEventPublisher: AuthEventPublisher
    @Mock lateinit var userServiceClient: UserServiceClient

    private lateinit var handler: AuthCodeGrantHandler
    private val props = AppProperties()

    private val clientId = "client-1"
    private val userId = UUID.randomUUID()
    private val redirectUri = "https://app.example.com/cb"
    private val codeVerifier = "abc123XYZverifier"
    private val codeChallenge: String = run {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private val client = OAuthClientEntity(
        clientId = clientId,
        clientName = "Test",
        clientSecretHash = "hash",
        grantTypes = "authorization_code",
        redirectUris = redirectUri,
        scopes = "openid email",
        accessTokenTtlSeconds = 3600,
        refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        handler = AuthCodeGrantHandler(
            authCodeStore, refreshTokenRepository, jwtIssuer,
            tokenFamilyService, authEventPublisher, userServiceClient, props,
        )
        whenever(jwtIssuer.issueAccessToken(any(), any(), any(), any(), any(), any(), any())).thenReturn("access-token")
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }
    }

    private fun codeData(scopes: List<String> = listOf("openid")) = AuthorizationCodeData(
        code = "code-1",
        clientId = clientId,
        userId = userId,
        redirectUri = redirectUri,
        scopes = scopes,
        codeChallenge = codeChallenge,
        codeChallengeMethod = "S256",
        nonce = "nonce1",
        sessionId = UUID.randomUUID().toString(),
    )

    @Test
    fun `handle returns TokenResponse with access token`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData())
        val params = mapOf("code" to "code-1", "code_verifier" to codeVerifier, "redirect_uri" to redirectUri)
        val response = handler.handle(params, client)
        assertEquals("access-token", response.accessToken)
        assertNotNull(response.refreshToken)
        assertNull(response.idToken) // no openid scope issued as id_token without openid handler
    }

    @Test
    fun `handle with openid scope returns id_token`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData(listOf("openid")))
        whenever(jwtIssuer.issueIdToken(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("id-token")
        val params = mapOf("code" to "code-1", "code_verifier" to codeVerifier, "redirect_uri" to redirectUri)
        val response = handler.handle(params, client)
        assertEquals("id-token", response.idToken)
    }

    @Test
    fun `handle throws InvalidGrantException when code not found`() {
        whenever(authCodeStore.consume("bad-code")).thenReturn(null)
        val params = mapOf("code" to "bad-code", "code_verifier" to codeVerifier, "redirect_uri" to redirectUri)
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
    }

    @Test
    fun `handle throws InvalidGrantException when PKCE fails`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData())
        val params = mapOf("code" to "code-1", "code_verifier" to "wrong-verifier", "redirect_uri" to redirectUri)
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
    }

    @Test
    fun `handle throws InvalidGrantException when redirect_uri mismatch`() {
        whenever(authCodeStore.consume("code-1")).thenReturn(codeData())
        val params = mapOf("code" to "code-1", "code_verifier" to codeVerifier, "redirect_uri" to "https://evil.com/cb")
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
    }

    @Test
    fun `supports returns true only for authorization_code`() {
        assert(handler.supports("authorization_code"))
        assert(!handler.supports("refresh_token"))
    }
}
```

- [ ] **Step 5.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.AuthCodeGrantHandlerTest"
```

Expected: `FAILED` — `AuthCodeGrantHandler` not found.

- [ ] **Step 5.3: Implement AuthCodeGrantHandler**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/AuthCodeGrantHandler.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.TokenResponse
import org.iamsso.services.authservice.service.UserServiceClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Component
class AuthCodeGrantHandler(
    private val authCodeStore: AuthorizationCodeStore,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer,
    private val tokenFamilyService: TokenFamilyService,
    private val authEventPublisher: AuthEventPublisher,
    private val userServiceClient: UserServiceClient,
    private val props: AppProperties,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "authorization_code"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val code = params["code"] ?: throw InvalidGrantException("Missing code")
        val codeVerifier = params["code_verifier"] ?: throw InvalidGrantException("Missing code_verifier")
        val redirectUri = params["redirect_uri"] ?: throw InvalidGrantException("Missing redirect_uri")

        val codeData = authCodeStore.consume(code) ?: throw InvalidGrantException("Invalid or expired code")

        if (codeData.clientId != client.clientId) throw InvalidGrantException("Code issued to different client")
        if (codeData.redirectUri != redirectUri) throw InvalidGrantException("redirect_uri mismatch")
        if (!verifyPkce(codeVerifier, codeData.codeChallenge)) throw InvalidGrantException("PKCE verification failed")

        val scopes = codeData.scopes
        val accessToken = jwtIssuer.issueAccessToken(
            userId = codeData.userId,
            clientId = client.clientId,
            scopes = scopes,
            sessionId = codeData.sessionId,
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        val idToken = if ("openid" in scopes) {
            val profile = if ("profile" in scopes) userServiceClient.getProfile(codeData.userId) else null
            jwtIssuer.issueIdToken(
                userId = codeData.userId,
                clientId = client.clientId,
                nonce = codeData.nonce,
                scopes = scopes,
                ttlSeconds = props.jwt.idTokenTtlSeconds,
                displayName = profile?.displayName,
                preferredUsername = profile?.displayName,
                firstName = profile?.firstName,
                lastName = profile?.lastName,
                locale = profile?.locale,
            )
        } else null

        val rawRefreshToken = UUID.randomUUID().toString()
        val tokenHash = sha256(rawRefreshToken)
        val familyId = UUID.randomUUID()
        val sessionUuid = codeData.sessionId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        refreshTokenRepository.save(RefreshTokenEntity(
            tokenHash = tokenHash,
            userId = codeData.userId,
            clientId = client.clientId,
            scopes = scopes.joinToString(" "),
            sessionId = sessionUuid,
            expiresAt = Instant.now().plusSeconds(client.refreshTokenTtlSeconds.toLong()),
            familyId = familyId,
        ))
        tokenFamilyService.initFamily(familyId, tokenHash, Duration.ofSeconds(client.refreshTokenTtlSeconds.toLong()))

        authEventPublisher.publishTokenIssued(
            userId = codeData.userId,
            clientId = client.clientId,
            grantType = "authorization_code",
            scopes = scopes,
            sessionId = codeData.sessionId,
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            refreshToken = rawRefreshToken,
            idToken = idToken,
            scope = scopes.joinToString(" "),
        )
    }

    private fun verifyPkce(codeVerifier: String, codeChallenge: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest) == codeChallenge
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
```

- [ ] **Step 5.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.AuthCodeGrantHandlerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/AuthCodeGrantHandler.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/AuthCodeGrantHandlerTest.kt
git commit -m "feat(auth-service): add AuthCodeGrantHandler (PKCE, id_token, refresh token)"
```

---

## Task 6: RefreshTokenGrantHandler

**Files:**
- Create: `.../authservice/service/grant/RefreshTokenGrantHandler.kt`
- Create: `test/.../authservice/service/grant/RefreshTokenGrantHandlerTest.kt`

- [ ] **Step 6.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/RefreshTokenGrantHandlerTest.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class RefreshTokenGrantHandlerTest {

    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var tokenFamilyService: TokenFamilyService
    @Mock lateinit var authEventPublisher: AuthEventPublisher

    private lateinit var handler: RefreshTokenGrantHandler
    private val props = AppProperties()

    private val clientId = "client-1"
    private val userId = UUID.randomUUID()
    private val familyId = UUID.randomUUID()
    private val rawToken = "my-refresh-token"
    private val tokenHash = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
    )

    private val client = OAuthClientEntity(
        clientId = clientId, clientName = "Test", clientSecretHash = "h",
        grantTypes = "refresh_token", redirectUris = "", scopes = "openid",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        handler = RefreshTokenGrantHandler(refreshTokenRepository, jwtIssuer, tokenFamilyService, authEventPublisher, props)
        whenever(jwtIssuer.issueAccessToken(any(), any(), any(), any(), any(), any(), any())).thenReturn("new-access-token")
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }
    }

    private fun activeToken(revoked: Boolean = false) = RefreshTokenEntity(
        tokenHash = tokenHash,
        userId = userId,
        clientId = clientId,
        scopes = "openid",
        sessionId = null,
        expiresAt = Instant.now().plusSeconds(3600),
        revoked = revoked,
        familyId = familyId,
    )

    @Test
    fun `handle rotates refresh token and returns new access token`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(activeToken())
        val params = mapOf("refresh_token" to rawToken)
        val response = handler.handle(params, client)
        assertNotNull(response.accessToken)
        assertNotNull(response.refreshToken)
        verify(refreshTokenRepository).save(any())
        verify(tokenFamilyService).addToFamily(any(), any())
    }

    @Test
    fun `handle detects reuse and revokes family`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(activeToken(revoked = true))
        val params = mapOf("refresh_token" to rawToken)
        assertThrows<InvalidGrantException> { handler.handle(params, client) }
        verify(tokenFamilyService).revokeFamily(familyId)
    }

    @Test
    fun `handle throws when token not found`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(null)
        assertThrows<InvalidGrantException> { handler.handle(mapOf("refresh_token" to rawToken), client) }
    }

    @Test
    fun `handle throws when token expired`() {
        val expired = RefreshTokenEntity(
            tokenHash = tokenHash, userId = userId, clientId = clientId,
            scopes = "openid", sessionId = null,
            expiresAt = Instant.now().minusSeconds(10), familyId = familyId,
        )
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(expired)
        assertThrows<InvalidGrantException> { handler.handle(mapOf("refresh_token" to rawToken), client) }
        verify(tokenFamilyService, never()).revokeFamily(any())
    }

    @Test
    fun `supports returns true only for refresh_token`() {
        assert(handler.supports("refresh_token"))
        assert(!handler.supports("authorization_code"))
    }
}
```

- [ ] **Step 6.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.RefreshTokenGrantHandlerTest"
```

Expected: `FAILED`

- [ ] **Step 6.3: Implement RefreshTokenGrantHandler**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/RefreshTokenGrantHandler.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.TokenResponse
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Component
class RefreshTokenGrantHandler(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer,
    private val tokenFamilyService: TokenFamilyService,
    private val authEventPublisher: AuthEventPublisher,
    private val props: AppProperties,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "refresh_token"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val rawToken = params["refresh_token"] ?: throw InvalidGrantException("Missing refresh_token")
        val hash = sha256(rawToken)
        val entity = refreshTokenRepository.findByTokenHash(hash) ?: throw InvalidGrantException("Invalid refresh token")

        if (entity.clientId != client.clientId) throw InvalidGrantException("Token issued to different client")

        if (entity.revoked) {
            entity.familyId?.let { tokenFamilyService.revokeFamily(it) }
            throw InvalidGrantException("Refresh token reuse detected")
        }

        if (entity.isExpired) throw InvalidGrantException("Refresh token expired")

        val newId = UUID.randomUUID()
        entity.revoked = true
        entity.replacedBy = newId
        refreshTokenRepository.save(entity)

        val scopes = entity.scopes.split(" ")
        val accessToken = jwtIssuer.issueAccessToken(
            userId = entity.userId,
            clientId = client.clientId,
            scopes = scopes,
            sessionId = entity.sessionId?.toString(),
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        val rawNewToken = UUID.randomUUID().toString()
        val newHash = sha256(rawNewToken)
        val familyId = entity.familyId ?: UUID.randomUUID()

        refreshTokenRepository.save(RefreshTokenEntity(
            id = newId,
            tokenHash = newHash,
            userId = entity.userId,
            clientId = client.clientId,
            scopes = entity.scopes,
            sessionId = entity.sessionId,
            expiresAt = Instant.now().plusSeconds(client.refreshTokenTtlSeconds.toLong()),
            familyId = familyId,
        ))
        tokenFamilyService.addToFamily(familyId, newHash)

        authEventPublisher.publishTokenIssued(
            userId = entity.userId,
            clientId = client.clientId,
            grantType = "refresh_token",
            scopes = scopes,
            sessionId = entity.sessionId?.toString(),
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            refreshToken = rawNewToken,
            scope = entity.scopes,
        )
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
```

- [ ] **Step 6.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.RefreshTokenGrantHandlerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/RefreshTokenGrantHandler.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/RefreshTokenGrantHandlerTest.kt
git commit -m "feat(auth-service): add RefreshTokenGrantHandler (rotation + reuse detection)"
```

---

## Task 7: ClientCredentialsGrantHandler

**Files:**
- Create: `.../authservice/service/grant/ClientCredentialsGrantHandler.kt`
- Create: `test/.../authservice/service/grant/ClientCredentialsGrantHandlerTest.kt`

- [ ] **Step 7.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/ClientCredentialsGrantHandlerTest.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidScopeException
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class ClientCredentialsGrantHandlerTest {

    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var authEventPublisher: AuthEventPublisher

    private lateinit var handler: ClientCredentialsGrantHandler

    private val client = OAuthClientEntity(
        clientId = "svc-client", clientName = "Service", clientSecretHash = "h",
        grantTypes = "client_credentials", redirectUris = "", scopes = "read write",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 0,
    )

    @BeforeEach
    fun setUp() {
        handler = ClientCredentialsGrantHandler(jwtIssuer, authEventPublisher)
        whenever(jwtIssuer.issueAccessToken(any(), any(), any(), any(), any(), any(), any())).thenReturn("svc-token")
    }

    @Test
    fun `handle issues access token without refresh token`() {
        val response = handler.handle(mapOf("scope" to "read"), client)
        assertNull(response.refreshToken)
        assertNull(response.idToken)
    }

    @Test
    fun `handle throws InvalidScopeException for unregistered scope`() {
        assertThrows<InvalidScopeException> { handler.handle(mapOf("scope" to "admin"), client) }
    }

    @Test
    fun `handle uses all client scopes when scope param absent`() {
        val response = handler.handle(emptyMap(), client)
        assertNull(response.refreshToken)
    }

    @Test
    fun `supports returns true only for client_credentials`() {
        assert(handler.supports("client_credentials"))
        assert(!handler.supports("authorization_code"))
    }
}
```

- [ ] **Step 7.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.ClientCredentialsGrantHandlerTest"
```

Expected: `FAILED`

- [ ] **Step 7.3: Implement ClientCredentialsGrantHandler**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/ClientCredentialsGrantHandler.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidScopeException
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenResponse
import org.springframework.stereotype.Component

@Component
class ClientCredentialsGrantHandler(
    private val jwtIssuer: JwtIssuer,
    private val authEventPublisher: AuthEventPublisher,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "client_credentials"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val clientScopes = client.scopeList()
        val requestedScopes = params["scope"]?.split(" ")?.filter { it.isNotBlank() } ?: clientScopes
        if (requestedScopes.any { it !in clientScopes }) throw InvalidScopeException("Requested scope not registered")

        val accessToken = jwtIssuer.issueAccessToken(
            userId = null,
            clientId = client.clientId,
            scopes = requestedScopes,
            sessionId = null,
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        authEventPublisher.publishTokenIssued(
            userId = client.clientId.let { java.util.UUID.fromString("00000000-0000-0000-0000-000000000000") },
            clientId = client.clientId,
            grantType = "client_credentials",
            scopes = requestedScopes,
            sessionId = null,
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            scope = requestedScopes.joinToString(" "),
        )
    }
}
```

> **Note:** `publishTokenIssued` requires a `userId: UUID` — for client_credentials we pass a zero UUID as placeholder. If `AuthEventPublisher.publishTokenIssued` is later refactored to accept `userId: UUID?`, update this call accordingly.

- [ ] **Step 7.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.ClientCredentialsGrantHandlerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/ClientCredentialsGrantHandler.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/ClientCredentialsGrantHandlerTest.kt
git commit -m "feat(auth-service): add ClientCredentialsGrantHandler"
```

---

## Task 8: DeviceCodeGrantHandler

**Files:**
- Create: `.../authservice/service/grant/DeviceCodeGrantHandler.kt`
- Create: `test/.../authservice/service/grant/DeviceCodeGrantHandlerTest.kt`

- [ ] **Step 8.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/DeviceCodeGrantHandlerTest.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.AccessDeniedException
import org.iamsso.services.authservice.exception.AuthorizationPendingException
import org.iamsso.services.authservice.exception.ExpiredTokenException
import org.iamsso.services.authservice.exception.SlowDownException
import org.iamsso.services.authservice.repository.DeviceCodeData
import org.iamsso.services.authservice.repository.DeviceCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class DeviceCodeGrantHandlerTest {

    @Mock lateinit var deviceCodeStore: DeviceCodeStore
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var tokenFamilyService: TokenFamilyService
    @Mock lateinit var authEventPublisher: AuthEventPublisher
    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>

    private lateinit var handler: DeviceCodeGrantHandler
    private val props = AppProperties()

    private val clientId = "device-client"
    private val userId = UUID.randomUUID()

    private val client = OAuthClientEntity(
        clientId = clientId, clientName = "Device", clientSecretHash = "h",
        grantTypes = "urn:ietf:params:oauth:grant-type:device_code",
        redirectUris = "", scopes = "openid",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        whenever(redis.opsForValue()).thenReturn(valueOps)
        handler = DeviceCodeGrantHandler(deviceCodeStore, refreshTokenRepository, jwtIssuer, tokenFamilyService, authEventPublisher, redis, props)
        whenever(jwtIssuer.issueAccessToken(any(), any(), any(), any(), any(), any(), any())).thenReturn("access-token")
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `handle returns tokens when device code approved`() {
        val deviceCode = "dc-1"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "ABCD-1234", clientId = clientId,
                scopes = listOf("openid"), userId = userId, approved = true)
        )
        whenever(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true)
        val response = handler.handle(mapOf("device_code" to deviceCode), client)
        assertNotNull(response.accessToken)
        assertNotNull(response.refreshToken)
    }

    @Test
    fun `handle throws AuthorizationPendingException when not yet approved`() {
        val deviceCode = "dc-2"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "EFGH-5678", clientId = clientId,
                scopes = listOf("openid"), approved = false)
        )
        whenever(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true)
        assertThrows<AuthorizationPendingException> { handler.handle(mapOf("device_code" to deviceCode), client) }
    }

    @Test
    fun `handle throws SlowDownException on rapid polling`() {
        val deviceCode = "dc-3"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "IJKL-9012", clientId = clientId,
                scopes = listOf("openid"), approved = false)
        )
        whenever(valueOps.setIfAbsent(any(), any(), any())).thenReturn(false)
        assertThrows<SlowDownException> { handler.handle(mapOf("device_code" to deviceCode), client) }
    }

    @Test
    fun `handle throws AccessDeniedException when denied`() {
        val deviceCode = "dc-4"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "MNOP-3456", clientId = clientId,
                scopes = listOf("openid"), denied = true)
        )
        assertThrows<AccessDeniedException> { handler.handle(mapOf("device_code" to deviceCode), client) }
    }

    @Test
    fun `handle throws ExpiredTokenException when device code not found`() {
        whenever(deviceCodeStore.findByDeviceCode("unknown")).thenReturn(null)
        assertThrows<ExpiredTokenException> { handler.handle(mapOf("device_code" to "unknown"), client) }
    }

    @Test
    fun `supports returns true only for device_code grant`() {
        assert(handler.supports("urn:ietf:params:oauth:grant-type:device_code"))
        assert(!handler.supports("authorization_code"))
    }
}
```

- [ ] **Step 8.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.DeviceCodeGrantHandlerTest"
```

Expected: `FAILED`

- [ ] **Step 8.3: Implement DeviceCodeGrantHandler**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/DeviceCodeGrantHandler.kt`:

```kotlin
package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.AccessDeniedException
import org.iamsso.services.authservice.exception.AuthorizationPendingException
import org.iamsso.services.authservice.exception.ExpiredTokenException
import org.iamsso.services.authservice.exception.InvalidGrantException
import org.iamsso.services.authservice.exception.SlowDownException
import org.iamsso.services.authservice.repository.DeviceCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.GrantHandler
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.iamsso.services.authservice.service.TokenResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Component
class DeviceCodeGrantHandler(
    private val deviceCodeStore: DeviceCodeStore,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer,
    private val tokenFamilyService: TokenFamilyService,
    private val authEventPublisher: AuthEventPublisher,
    private val redis: StringRedisTemplate,
    private val props: AppProperties,
) : GrantHandler {

    override fun supports(grantType: String) = grantType == "urn:ietf:params:oauth:grant-type:device_code"

    override fun handle(params: Map<String, String>, client: OAuthClientEntity): TokenResponse {
        val deviceCode = params["device_code"] ?: throw InvalidGrantException("Missing device_code")
        val data = deviceCodeStore.findByDeviceCode(deviceCode)
            ?: throw ExpiredTokenException("Device code expired or not found")

        if (data.denied) throw AccessDeniedException("User denied the device authorization")

        if (!data.approved) {
            val pollKey = "auth:device:poll:$deviceCode"
            val notRapid = redis.opsForValue().setIfAbsent(
                pollKey, "1", Duration.ofSeconds(props.deviceCode.pollingIntervalSeconds.toLong())
            ) ?: true
            if (!notRapid) throw SlowDownException()
            throw AuthorizationPendingException()
        }

        val userId = data.userId ?: throw InvalidGrantException("Approved but userId missing")
        val scopes = data.scopes
        val accessToken = jwtIssuer.issueAccessToken(
            userId = userId,
            clientId = client.clientId,
            scopes = scopes,
            sessionId = null,
            ttlSeconds = client.accessTokenTtlSeconds.toLong(),
        )

        val rawRefreshToken = UUID.randomUUID().toString()
        val tokenHash = sha256(rawRefreshToken)
        val familyId = UUID.randomUUID()

        refreshTokenRepository.save(RefreshTokenEntity(
            tokenHash = tokenHash,
            userId = userId,
            clientId = client.clientId,
            scopes = scopes.joinToString(" "),
            sessionId = null,
            expiresAt = Instant.now().plusSeconds(client.refreshTokenTtlSeconds.toLong()),
            familyId = familyId,
        ))
        tokenFamilyService.initFamily(familyId, tokenHash, Duration.ofSeconds(client.refreshTokenTtlSeconds.toLong()))

        deviceCodeStore.delete(data)

        authEventPublisher.publishTokenIssued(
            userId = userId,
            clientId = client.clientId,
            grantType = "urn:ietf:params:oauth:grant-type:device_code",
            scopes = scopes,
            sessionId = null,
        )

        return TokenResponse(
            accessToken = accessToken,
            expiresIn = client.accessTokenTtlSeconds.toLong(),
            refreshToken = rawRefreshToken,
            scope = scopes.joinToString(" "),
        )
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
```

- [ ] **Step 8.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.grant.DeviceCodeGrantHandlerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/grant/DeviceCodeGrantHandler.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/grant/DeviceCodeGrantHandlerTest.kt
git commit -m "feat(auth-service): add DeviceCodeGrantHandler (slow_down + reuse detection)"
```

---

## Task 9: TokenService

**Files:**
- Create: `.../authservice/service/TokenService.kt`
- Create: `test/.../authservice/service/TokenServiceTest.kt`

- [ ] **Step 9.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/TokenServiceTest.kt`:

```kotlin
package org.iamsso.services.authservice.service

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidClientException
import org.iamsso.services.authservice.exception.UnsupportedGrantTypeException
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Base64
import java.util.Optional
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class TokenServiceTest {

    @Mock lateinit var clientRepository: OAuthClientRepository
    @Mock lateinit var request: HttpServletRequest

    private val passwordEncoder = BCryptPasswordEncoder()
    private val secret = "my-secret"
    private lateinit var service: TokenService

    private val client = OAuthClientEntity(
        clientId = "client-1",
        clientName = "Test",
        clientSecretHash = BCryptPasswordEncoder().encode("my-secret"),
        grantTypes = "authorization_code",
        redirectUris = "",
        scopes = "openid",
        accessTokenTtlSeconds = 3600,
        refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        service = TokenService(clientRepository, passwordEncoder, emptyList())
        whenever(request.getHeader("Authorization")).thenReturn(null)
    }

    @Test
    fun `authenticateClient succeeds with form params`() {
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val params = mapOf("client_id" to "client-1", "client_secret" to secret)
        val result = service.authenticateClient(params, request)
        assertEquals("client-1", result.clientId)
    }

    @Test
    fun `authenticateClient succeeds with Basic auth header`() {
        val credentials = Base64.getEncoder().encodeToString("client-1:my-secret".toByteArray())
        whenever(request.getHeader("Authorization")).thenReturn("Basic $credentials")
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val result = service.authenticateClient(emptyMap(), request)
        assertEquals("client-1", result.clientId)
    }

    @Test
    fun `authenticateClient throws InvalidClientException for wrong secret`() {
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val params = mapOf("client_id" to "client-1", "client_secret" to "wrong")
        assertThrows<InvalidClientException> { service.authenticateClient(params, request) }
    }

    @Test
    fun `authenticateClient throws InvalidClientException for unknown client`() {
        whenever(clientRepository.findById("unknown")).thenReturn(Optional.empty())
        val params = mapOf("client_id" to "unknown", "client_secret" to secret)
        assertThrows<InvalidClientException> { service.authenticateClient(params, request) }
    }

    @Test
    fun `issue throws UnsupportedGrantTypeException when no handler matches`() {
        whenever(clientRepository.findById("client-1")).thenReturn(Optional.of(client))
        val params = mapOf("client_id" to "client-1", "client_secret" to secret, "grant_type" to "authorization_code")
        assertThrows<UnsupportedGrantTypeException> { service.issue(params, request) }
    }
}
```

- [ ] **Step 9.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.TokenServiceTest"
```

Expected: `FAILED`

- [ ] **Step 9.3: Implement TokenService**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/TokenService.kt`:

```kotlin
package org.iamsso.services.authservice.service

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidClientException
import org.iamsso.services.authservice.exception.UnsupportedGrantTypeException
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64

@Service
class TokenService(
    private val clientRepository: OAuthClientRepository,
    private val passwordEncoder: PasswordEncoder,
    private val handlers: List<GrantHandler>,
) {
    fun issue(params: Map<String, String>, request: HttpServletRequest): TokenResponse {
        val client = authenticateClient(params, request)
        val grantType = params["grant_type"] ?: throw UnsupportedGrantTypeException("Missing grant_type")
        val handler = handlers.firstOrNull { it.supports(grantType) }
            ?: throw UnsupportedGrantTypeException("Unsupported grant type: $grantType")
        return handler.handle(params, client)
    }

    fun authenticateClient(params: Map<String, String>, request: HttpServletRequest): OAuthClientEntity {
        val (clientId, clientSecret) = extractCredentials(params, request)
        val client = clientRepository.findById(clientId).orElseThrow {
            InvalidClientException("Unknown client: $clientId")
        }
        if (!passwordEncoder.matches(clientSecret, client.clientSecretHash)) {
            // Check grace period for rotated secret
            if (client.previousSecretHash != null &&
                client.previousSecretExpiresAt != null &&
                client.previousSecretExpiresAt!!.isAfter(Instant.now()) &&
                passwordEncoder.matches(clientSecret, client.previousSecretHash)
            ) {
                return client
            }
            throw InvalidClientException("Invalid client credentials")
        }
        return client
    }

    private fun extractCredentials(params: Map<String, String>, request: HttpServletRequest): Pair<String, String> {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            val decoded = String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
            val colon = decoded.indexOf(':')
            if (colon > 0) return decoded.substring(0, colon) to decoded.substring(colon + 1)
        }
        val clientId = params["client_id"] ?: throw InvalidClientException("Missing client_id")
        val clientSecret = params["client_secret"] ?: throw InvalidClientException("Missing client_secret")
        return clientId to clientSecret
    }
}
```

- [ ] **Step 9.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.service.TokenServiceTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/service/TokenService.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/service/TokenServiceTest.kt
git commit -m "feat(auth-service): add TokenService (client auth + grant routing)"
```

---

## Task 10: TokenController

**Files:**
- Create: `.../authservice/controller/TokenController.kt`
- Create: `test/.../authservice/controller/TokenControllerTest.kt`

- [ ] **Step 10.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/TokenControllerTest.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.exception.UnsupportedGrantTypeException
import org.iamsso.services.authservice.service.TokenResponse
import org.iamsso.services.authservice.service.TokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class TokenControllerTest {

    @Mock lateinit var tokenService: TokenService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(TokenController(tokenService))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
    }

    @Test
    fun `POST token returns 200 with token response`() {
        val tokenResponse = TokenResponse(
            accessToken = "at123",
            expiresIn = 3600,
            scope = "openid",
        )
        whenever(tokenService.issue(any(), any<HttpServletRequest>())).thenReturn(tokenResponse)

        mockMvc.post("/oauth2/token") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("grant_type", "authorization_code")
            param("client_id", "client-1")
            param("client_secret", "secret")
            param("code", "some-code")
            param("code_verifier", "verifier")
            param("redirect_uri", "https://app.example.com/cb")
        }.andExpect {
            status { isOk() }
            jsonPath("$.access_token") { value("at123") }
            jsonPath("$.token_type") { value("Bearer") }
            jsonPath("$.expires_in") { value(3600) }
        }
    }

    @Test
    fun `POST token returns 400 on unsupported grant type`() {
        whenever(tokenService.issue(any(), any<HttpServletRequest>())).thenThrow(
            UnsupportedGrantTypeException("Unsupported")
        )
        mockMvc.post("/oauth2/token") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("grant_type", "implicit")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("unsupported_grant_type") }
        }
    }
}
```

- [ ] **Step 10.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.TokenControllerTest"
```

Expected: `FAILED`

- [ ] **Step 10.3: Implement TokenController**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/TokenController.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.service.TokenResponse
import org.iamsso.services.authservice.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TokenController(private val tokenService: TokenService) {

    @PostMapping("/oauth2/token", consumes = ["application/x-www-form-urlencoded"])
    fun token(
        @RequestParam params: Map<String, String>,
        request: HttpServletRequest,
    ): TokenResponse = tokenService.issue(params, request)
}
```

- [ ] **Step 10.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.TokenControllerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/TokenController.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/TokenControllerTest.kt
git commit -m "feat(auth-service): add TokenController (POST /oauth2/token)"
```

---

## Task 11: RevocationController

**Files:**
- Create: `.../authservice/controller/RevocationController.kt`
- Create: `test/.../authservice/controller/RevocationControllerTest.kt`

- [ ] **Step 11.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/RevocationControllerTest.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.TokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RevocationControllerTest {

    @Mock lateinit var tokenService: TokenService
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var mockMvc: MockMvc

    private val rawToken = "my-token"
    private val tokenHash = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
    )
    private val client = OAuthClientEntity(
        clientId = "client-1", clientName = "Test", clientSecretHash = "h",
        grantTypes = "", redirectUris = "", scopes = "",
        accessTokenTtlSeconds = 0, refreshTokenTtlSeconds = 0,
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(RevocationController(tokenService, refreshTokenRepository))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
    }

    @Test
    fun `POST revoke revokes token and returns 200`() {
        val entity = RefreshTokenEntity(
            tokenHash = tokenHash, userId = UUID.randomUUID(), clientId = "client-1",
            scopes = "openid", sessionId = null,
            expiresAt = Instant.now().plusSeconds(3600),
        )
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(entity)
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }

        mockMvc.post("/oauth2/revoke") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", rawToken)
        }.andExpect { status { isOk() } }

        verify(refreshTokenRepository).save(any())
    }

    @Test
    fun `POST revoke returns 200 even when token not found (RFC 7009)`() {
        whenever(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(null)

        mockMvc.post("/oauth2/revoke") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", rawToken)
        }.andExpect { status { isOk() } }

        verify(refreshTokenRepository, never()).save(any())
    }
}
```

- [ ] **Step 11.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.RevocationControllerTest"
```

Expected: `FAILED`

- [ ] **Step 11.3: Implement RevocationController**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/RevocationController.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.Base64

@RestController
class RevocationController(
    private val tokenService: TokenService,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @PostMapping("/oauth2/revoke", consumes = ["application/x-www-form-urlencoded"])
    fun revoke(
        @RequestParam params: Map<String, String>,
        request: HttpServletRequest,
    ) {
        val client = tokenService.authenticateClient(params, request)
        val token = params["token"] ?: return
        val hash = sha256(token)
        val entity = refreshTokenRepository.findByTokenHash(hash)
        if (entity != null && entity.clientId == client.clientId) {
            entity.revoked = true
            refreshTokenRepository.save(entity)
        }
        // RFC 7009: always 200, even if token not found
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
```

- [ ] **Step 11.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.RevocationControllerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/RevocationController.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/RevocationControllerTest.kt
git commit -m "feat(auth-service): add RevocationController (POST /oauth2/revoke, RFC 7009)"
```

---

## Task 12: IntrospectionController

**Files:**
- Create: `.../authservice/controller/IntrospectionController.kt`
- Create: `test/.../authservice/controller/IntrospectionControllerTest.kt`

- [ ] **Step 12.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/IntrospectionControllerTest.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import com.nimbusds.jwt.JWTClaimsSet
import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class IntrospectionControllerTest {

    @Mock lateinit var tokenService: TokenService
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var mockMvc: MockMvc

    private val client = OAuthClientEntity(
        clientId = "client-1", clientName = "Test", clientSecretHash = "h",
        grantTypes = "", redirectUris = "", scopes = "",
        accessTokenTtlSeconds = 0, refreshTokenTtlSeconds = 0,
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(IntrospectionController(tokenService, jwtIssuer, refreshTokenRepository))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
    }

    @Test
    fun `POST introspect returns active true for valid JWT`() {
        val claims = JWTClaimsSet.Builder()
            .subject("user-1")
            .audience("client-1")
            .issuer("http://localhost:8080")
            .claim("scope", "openid")
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .build()
        whenever(jwtIssuer.verify("valid-jwt")).thenReturn(claims)

        mockMvc.post("/oauth2/introspect") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", "valid-jwt")
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(true) }
            jsonPath("$.sub") { value("user-1") }
        }
    }

    @Test
    fun `POST introspect returns active false for invalid JWT`() {
        whenever(jwtIssuer.verify(any())).thenReturn(null)
        whenever(refreshTokenRepository.findByTokenHash(any())).thenReturn(null)

        mockMvc.post("/oauth2/introspect") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", "garbage")
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(false) }
        }
    }

    @Test
    fun `POST introspect returns active true for active refresh token`() {
        whenever(jwtIssuer.verify(any())).thenReturn(null)
        val rawToken = "rt-token"
        val hash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
        )
        val entity = RefreshTokenEntity(
            tokenHash = hash, userId = UUID.randomUUID(), clientId = "client-1",
            scopes = "openid", sessionId = null,
            expiresAt = Instant.now().plusSeconds(3600),
        )
        whenever(refreshTokenRepository.findByTokenHash(hash)).thenReturn(entity)

        mockMvc.post("/oauth2/introspect") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", rawToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(true) }
        }
    }
}
```

- [ ] **Step 12.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.IntrospectionControllerTest"
```

Expected: `FAILED`

- [ ] **Step 12.3: Implement IntrospectionController**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/IntrospectionController.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.Base64

@RestController
class IntrospectionController(
    private val tokenService: TokenService,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @PostMapping("/oauth2/introspect", consumes = ["application/x-www-form-urlencoded"])
    fun introspect(
        @RequestParam params: Map<String, String>,
        request: HttpServletRequest,
    ): Map<String, Any?> {
        tokenService.authenticateClient(params, request)
        val token = params["token"] ?: return mapOf("active" to false)

        val claims = jwtIssuer.verify(token)
        if (claims != null) {
            return mapOf(
                "active" to true,
                "sub" to claims.subject,
                "scope" to claims.getStringClaim("scope"),
                "client_id" to claims.audience?.firstOrNull(),
                "exp" to claims.expirationTime.time / 1000,
                "iss" to claims.issuer,
            )
        }

        val hash = sha256(token)
        val entity = refreshTokenRepository.findByTokenHash(hash)
        return if (entity != null && entity.isActive) {
            mapOf(
                "active" to true,
                "sub" to entity.userId.toString(),
                "scope" to entity.scopes,
                "client_id" to entity.clientId,
                "exp" to entity.expiresAt.epochSecond,
            )
        } else {
            mapOf("active" to false)
        }
    }

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
}
```

- [ ] **Step 12.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.IntrospectionControllerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 12.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/IntrospectionController.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/IntrospectionControllerTest.kt
git commit -m "feat(auth-service): add IntrospectionController (POST /oauth2/introspect)"
```

---

## Task 13: DeviceController

**Files:**
- Create: `.../authservice/controller/DeviceController.kt`
- Create: `test/.../authservice/controller/DeviceControllerTest.kt`

- [ ] **Step 13.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/DeviceControllerTest.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.exception.InvalidScopeException
import org.iamsso.services.authservice.exception.OAuthExceptionHandler
import org.iamsso.services.authservice.repository.DeviceCodeStore
import org.iamsso.services.authservice.service.TokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class DeviceControllerTest {

    @Mock lateinit var tokenService: TokenService
    @Mock lateinit var deviceCodeStore: DeviceCodeStore

    private lateinit var mockMvc: MockMvc
    private val props = AppProperties()

    private val client = OAuthClientEntity(
        clientId = "client-1", clientName = "Test", clientSecretHash = "h",
        grantTypes = "urn:ietf:params:oauth:grant-type:device_code",
        redirectUris = "", scopes = "openid email",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(DeviceController(tokenService, deviceCodeStore, props))
            .setControllerAdvice(OAuthExceptionHandler())
            .build()
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
    }

    @Test
    fun `POST device_authorization returns device_code and user_code`() {
        mockMvc.post("/oauth2/device_authorization") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("scope", "openid")
        }.andExpect {
            status { isOk() }
            jsonPath("$.device_code") { exists() }
            jsonPath("$.user_code") { exists() }
            jsonPath("$.verification_uri") { exists() }
            jsonPath("$.expires_in") { value(600) }
            jsonPath("$.interval") { value(5) }
        }
    }

    @Test
    fun `POST device_authorization returns 400 for invalid scope`() {
        whenever(tokenService.authenticateClient(any(), any<HttpServletRequest>())).thenReturn(client)
        mockMvc.post("/oauth2/device_authorization") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("scope", "admin")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_scope") }
        }
    }
}
```

- [ ] **Step 13.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.DeviceControllerTest"
```

Expected: `FAILED`

- [ ] **Step 13.3: Implement DeviceController**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/DeviceController.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.exception.InvalidScopeException
import org.iamsso.services.authservice.repository.DeviceCodeData
import org.iamsso.services.authservice.repository.DeviceCodeStore
import org.iamsso.services.authservice.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class DeviceController(
    private val tokenService: TokenService,
    private val deviceCodeStore: DeviceCodeStore,
    private val props: AppProperties,
) {

    @PostMapping("/oauth2/device_authorization", consumes = ["application/x-www-form-urlencoded"])
    fun deviceAuthorization(
        @RequestParam params: Map<String, String>,
        request: HttpServletRequest,
    ): DeviceAuthorizationResponse {
        val client = tokenService.authenticateClient(params, request)
        val clientScopes = client.scopeList()
        val requestedScopes = params["scope"]?.split(" ")?.filter { it.isNotBlank() } ?: clientScopes
        if (requestedScopes.any { it !in clientScopes }) throw InvalidScopeException("Requested scope not registered")

        val deviceCode = UUID.randomUUID().toString()
        val userCode = generateUserCode()
        deviceCodeStore.save(DeviceCodeData(
            deviceCode = deviceCode,
            userCode = userCode,
            clientId = client.clientId,
            scopes = requestedScopes,
        ))

        return DeviceAuthorizationResponse(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = "${props.loginPage.url}/device",
            expiresIn = props.deviceCode.ttlSeconds,
            interval = props.deviceCode.pollingIntervalSeconds,
        )
    }

    private fun generateUserCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..4).map { chars.random() }.joinToString("") + "-" + (1..4).map { chars.random() }.joinToString("")
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeviceAuthorizationResponse(
    @JsonProperty("device_code") val deviceCode: String,
    @JsonProperty("user_code") val userCode: String,
    @JsonProperty("verification_uri") val verificationUri: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    val interval: Int,
)
```

- [ ] **Step 13.4: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.DeviceControllerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 13.5: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/DeviceController.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/DeviceControllerTest.kt
git commit -m "feat(auth-service): add DeviceController (POST /oauth2/device_authorization)"
```

---

## Task 14: DiscoveryController + JwksController

**Files:**
- Create: `.../authservice/controller/DiscoveryController.kt`
- Modify: `.../authservice/controller/JwksController.kt` — fill stub
- Create: `test/.../authservice/controller/DiscoveryControllerTest.kt`

- [ ] **Step 14.1: Write failing test**

Create `services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/DiscoveryControllerTest.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.AppProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DiscoveryControllerTest {

    private lateinit var mockMvc: MockMvc
    private val props = AppProperties(issuer = "http://localhost:8080")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(DiscoveryController(props)).build()
    }

    @Test
    fun `GET openid-configuration returns required OIDC Discovery fields`() {
        mockMvc.get("/.well-known/openid-configuration")
            .andExpect {
                status { isOk() }
                jsonPath("$.issuer") { value("http://localhost:8080") }
                jsonPath("$.authorization_endpoint") { value("http://localhost:8080/oauth2/authorize") }
                jsonPath("$.token_endpoint") { value("http://localhost:8080/oauth2/token") }
                jsonPath("$.userinfo_endpoint") { value("http://localhost:8080/userinfo") }
                jsonPath("$.jwks_uri") { value("http://localhost:8080/.well-known/jwks.json") }
                jsonPath("$.revocation_endpoint") { value("http://localhost:8080/oauth2/revoke") }
                jsonPath("$.introspection_endpoint") { value("http://localhost:8080/oauth2/introspect") }
                jsonPath("$.device_authorization_endpoint") { value("http://localhost:8080/oauth2/device_authorization") }
                jsonPath("$.grant_types_supported") { isArray() }
                jsonPath("$.id_token_signing_alg_values_supported[0]") { value("RS256") }
            }
    }
}
```

- [ ] **Step 14.2: Run test to verify it fails**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.DiscoveryControllerTest"
```

Expected: `FAILED`

- [ ] **Step 14.3: Implement DiscoveryController**

Create `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/DiscoveryController.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.AppProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DiscoveryController(private val props: AppProperties) {

    @GetMapping("/.well-known/openid-configuration")
    fun discovery(): Map<String, Any> {
        val issuer = props.issuer
        return mapOf(
            "issuer" to issuer,
            "authorization_endpoint" to "$issuer/oauth2/authorize",
            "token_endpoint" to "$issuer/oauth2/token",
            "userinfo_endpoint" to "$issuer/userinfo",
            "jwks_uri" to "$issuer/.well-known/jwks.json",
            "revocation_endpoint" to "$issuer/oauth2/revoke",
            "introspection_endpoint" to "$issuer/oauth2/introspect",
            "device_authorization_endpoint" to "$issuer/oauth2/device_authorization",
            "scopes_supported" to listOf("openid", "email", "profile"),
            "response_types_supported" to listOf("code"),
            "grant_types_supported" to listOf(
                "authorization_code",
                "refresh_token",
                "client_credentials",
                "urn:ietf:params:oauth:grant-type:device_code",
            ),
            "token_endpoint_auth_methods_supported" to listOf("client_secret_basic", "client_secret_post"),
            "id_token_signing_alg_values_supported" to listOf("RS256"),
            "code_challenge_methods_supported" to listOf("S256"),
        )
    }
}
```

- [ ] **Step 14.4: Fill JwksController stub**

Replace the full content of `services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/JwksController.kt`:

```kotlin
package org.iamsso.services.authservice.controller

import com.nimbusds.jose.jwk.JWKSet
import org.iamsso.services.authservice.config.JwtKeyProvider
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwksController(private val jwtKeyProvider: JwtKeyProvider) {

    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> {
        val jwkSet = JWKSet(jwtKeyProvider.rsaKey.toPublicJWK())
        @Suppress("UNCHECKED_CAST")
        return jwkSet.toJSONObject() as Map<String, Any>
    }
}
```

- [ ] **Step 14.5: Run test to verify it passes**

```bash
./gradlew :services:auth-service:test --tests "org.iamsso.services.authservice.controller.DiscoveryControllerTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 14.6: Commit**

```bash
git add \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/DiscoveryController.kt \
  services/auth-service/src/main/kotlin/org/iamsso/services/authservice/controller/JwksController.kt \
  services/auth-service/src/test/kotlin/org/iamsso/services/authservice/controller/DiscoveryControllerTest.kt
git commit -m "feat(auth-service): add DiscoveryController and fill JwksController stub"
```

---

## Task 15: SecurityConfig update + final compile

**Files:**
- Modify: `.../authservice/config/SecurityConfig.kt`

- [ ] **Step 15.1: Update Chain 1 securityMatcher**

The current Chain 1 matcher is:
```
/oauth2/**, /.well-known/**, /userinfo, /actuator/**
```

`/oauth2/**` already covers `/oauth2/token`, `/oauth2/revoke`, `/oauth2/introspect`, `/oauth2/device_authorization`. `/.well-known/**` already covers `/.well-known/jwks.json` and `/.well-known/openid-configuration`. No change needed to the matcher.

Verify the existing config matches exactly:

```kotlin
.securityMatcher("/oauth2/**", "/.well-known/**", "/userinfo", "/actuator/**")
```

If it already looks like the above — no change needed, skip to Step 15.2.

- [ ] **Step 15.2: Run full test suite**

```bash
./gradlew :services:auth-service:test
```

Expected: `BUILD SUCCESSFUL`. All tests pass.

If `AuthServiceApplicationTests.contextLoads` fails because the Spring context can't start (missing beans or configuration issues), check that:
- All `@Component` / `@Service` beans have their dependencies available
- `application-test.yaml` disables Liquibase (`liquibase.enabled: false`) so H2 DDL runs instead
- Kafka listener auto-startup is false in test profile

- [ ] **Step 15.3: Commit any SecurityConfig changes (if made)**

```bash
# Only if SecurityConfig was modified:
git add services/auth-service/src/main/kotlin/org/iamsso/services/authservice/config/SecurityConfig.kt
git commit -m "feat(auth-service): update SecurityConfig Chain 1 for new endpoints"
```

---

## Self-Review

After writing the plan, checked against spec:

| Spec requirement | Task |
|---|---|
| authorization_code grant + PKCE | Task 5 |
| refresh_token rotation + reuse detection | Task 6 |
| client_credentials | Task 7 |
| device_code grant | Task 8 |
| TokenService client auth (Basic + form) | Task 9 |
| TokenController POST /oauth2/token | Task 10 |
| RevocationController POST /oauth2/revoke | Task 11 |
| IntrospectionController POST /oauth2/introspect | Task 12 |
| DeviceController POST /oauth2/device_authorization | Task 13 |
| DiscoveryController GET /.well-known/openid-configuration | Task 14 |
| JwksController GET /.well-known/jwks.json | Task 14 |
| ID Token on openid scope | Task 5 (AuthCodeGrantHandler) |
| TokenFamilyService reuse detection (Redis) | Task 3, Task 6 |
| DB migration 002 family_id | Task 1 |
| AppProperties.tokens | Task 1 |
| Unit tests for all grant handlers | Tasks 5–8 |
| Integration tests for all controllers | Tasks 10–14 |

All spec requirements covered.
