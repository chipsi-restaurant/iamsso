package org.iamsso.services.authservice.service

import org.iamsso.services.authservice.config.AppProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

data class MfaStatus(val userId: UUID, val mfaEnabled: Boolean, val activeFactors: List<String>)
data class MfaVerifyResult(val valid: Boolean)

@Component
class MfaServiceClient(props: AppProperties) {

    private val restClient = RestClient.create(props.mfaService.baseUrl)

    fun getStatus(userId: UUID): MfaStatus? = runCatchingRest {
        restClient.get()
            .uri("/api/v1/mfa/$userId/status")
            .retrieve()
            .body(MfaStatus::class.java)
    }

    fun sendOtp(userId: UUID, email: String) {
        try {
            restClient.post()
                .uri("/api/v1/mfa/$userId/send-otp")
                .headers { it.set("X-User-Email", email) }
                .retrieve()
                .toBodilessEntity()
        } catch (_: RestClientResponseException) {}
    }

    fun verify(userId: UUID, code: String, factorType: String? = null): MfaVerifyResult? = runCatchingRest {
        val body = mutableMapOf<String, Any>("code" to code)
        if (factorType != null) body["factorType"] = factorType
        restClient.post()
            .uri("/api/v1/mfa/$userId/verify")
            .headers { it.set("Content-Type", "application/json") }
            .body(body)
            .retrieve()
            .body(MfaVerifyResult::class.java)
    }

    private fun <T> runCatchingRest(block: () -> T): T? =
        try { block() } catch (_: RestClientResponseException) { null }
}
