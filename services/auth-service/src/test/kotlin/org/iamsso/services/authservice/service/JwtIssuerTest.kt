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
    fun `issueIdToken includes nonce`() {
        val userId = UUID.randomUUID()
        val token = jwtIssuer.issueIdToken(
            userId = userId,
            clientId = "client",
            nonce = "abc123",
            scopes = listOf("openid"),
            ttlSeconds = 3600,
        )
        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertEquals(userId.toString(), claims.subject)
        assertEquals("abc123", claims.getStringClaim("nonce"))
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
