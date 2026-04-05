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
