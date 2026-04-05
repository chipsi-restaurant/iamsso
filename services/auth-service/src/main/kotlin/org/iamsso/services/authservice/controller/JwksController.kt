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
