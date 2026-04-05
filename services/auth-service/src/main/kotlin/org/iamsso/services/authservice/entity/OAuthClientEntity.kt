package org.iamsso.services.authservice.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "oauth_clients", schema = "iamsso_auth")
class OAuthClientEntity(
    @Id
    @Column(name = "client_id")
    val clientId: String = "",

    @Column(name = "client_name", nullable = false, unique = true)
    var clientName: String = "",

    @Column(name = "client_secret_hash", nullable = false)
    var clientSecretHash: String = "",

    @Column(name = "previous_secret_hash")
    var previousSecretHash: String? = null,

    @Column(name = "previous_secret_expires_at")
    var previousSecretExpiresAt: Instant? = null,

    @Column(name = "grant_types", nullable = false)
    var grantTypes: String = "",

    @Column(name = "redirect_uris", nullable = false)
    var redirectUris: String = "",

    @Column(nullable = false)
    var scopes: String = "",

    @Column(name = "token_endpoint_auth_method", nullable = false)
    var tokenEndpointAuthMethod: String = "client_secret_post",

    @Column(name = "access_token_ttl_seconds", nullable = false)
    var accessTokenTtlSeconds: Int = 3600,

    @Column(name = "refresh_token_ttl_seconds", nullable = false)
    var refreshTokenTtlSeconds: Int = 2592000,

    @Column(name = "device_code_ttl_seconds")
    var deviceCodeTtlSeconds: Int = 600,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    fun grantTypeList(): List<String> = grantTypes.split(",").map { it.trim() }
    fun redirectUriList(): List<String> = redirectUris.split(",").map { it.trim() }
    fun scopeList(): List<String> = scopes.split(",").map { it.trim() }
}