package org.iamsso.services.authservice.service

import org.iamsso.services.authservice.config.AppProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Instant
import java.util.UUID

data class SessionData(
    val sessionId: String = "",
    val userId: UUID = UUID.randomUUID(),
    val clientIds: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val lastActivityAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now(),
)

@Component
class SessionServiceClient(props: AppProperties) {

    private val restClient = RestClient.create(props.sessionService.baseUrl)

    fun create(userId: UUID, clientId: String): SessionData? = runCatchingRest {
        restClient.post()
            .uri("/api/v1/sessions")
            .headers { it.set("Content-Type", "application/json") }
            .body(mapOf("userId" to userId, "clientId" to clientId))
            .retrieve()
            .body(SessionData::class.java)
    }

    fun get(sessionId: String): SessionData? = runCatchingRest {
        restClient.get()
            .uri("/api/v1/sessions/$sessionId")
            .retrieve()
            .body(SessionData::class.java)
    }

    fun updateActivity(sessionId: String) {
        try {
            restClient.post()
                .uri("/api/v1/sessions/$sessionId/activity")
                .retrieve()
                .toBodilessEntity()
        } catch (_: RestClientResponseException) {}
    }

    fun addClient(sessionId: String, clientId: String) {
        try {
            restClient.post()
                .uri("/api/v1/sessions/$sessionId/clients")
                .headers { it.set("Content-Type", "application/json") }
                .body(mapOf("clientId" to clientId))
                .retrieve()
                .toBodilessEntity()
        } catch (_: RestClientResponseException) {}
    }

    fun delete(sessionId: String) {
        try {
            restClient.delete()
                .uri("/api/v1/sessions/$sessionId")
                .retrieve()
                .toBodilessEntity()
        } catch (_: RestClientResponseException) {}
    }

    fun deleteAllForUser(userId: UUID) {
        try {
            restClient.delete()
                .uri("/api/v1/sessions/user/$userId")
                .retrieve()
                .toBodilessEntity()
        } catch (_: RestClientResponseException) {}
    }

    private fun <T> runCatchingRest(block: () -> T): T? =
        try { block() } catch (_: RestClientResponseException) { null }
}
