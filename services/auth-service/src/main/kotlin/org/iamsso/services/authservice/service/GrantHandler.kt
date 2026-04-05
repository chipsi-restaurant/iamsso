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
