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
