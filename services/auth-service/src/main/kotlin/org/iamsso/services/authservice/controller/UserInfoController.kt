package org.iamsso.services.authservice.controller

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import org.iamsso.contracts.auth.model.UserInfoResponse
import org.iamsso.services.authservice.config.JwtKeyProvider
import org.iamsso.services.authservice.service.UserServiceClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI
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
            val verifier = RSASSAVerifier(jwtKeyProvider.publicKey)
            if (!signed.verify(verifier)) throw IllegalArgumentException("invalid signature")
            signed.jwtClaimsSet
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }

        val sub = jwt.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing sub")
        val scope = jwt.getStringClaim("scope") ?: ""
        val scopes = scope.split(" ")

        val email = if ("email" in scopes) jwt.getStringClaim("email") else null
        val emailVerified = if ("email" in scopes) jwt.getBooleanClaim("email_verified") else null

        val profile = if ("profile" in scopes) userServiceClient.getProfile(UUID.fromString(sub)) else null

        return UserInfoResponse(
            sub = sub,
            email = email,
            emailVerified = emailVerified,
            preferredUsername = profile?.displayName,
            name = listOfNotNull(profile?.firstName, profile?.lastName).joinToString(" ").ifBlank { null },
            givenName = profile?.firstName,
            familyName = profile?.lastName,
            locale = profile?.locale,
            picture = profile?.avatarUrl?.let { runCatching { URI.create(it) }.getOrNull() },
        )
    }
}
