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

    @PostMapping("/oauth2/device_authorization")
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
