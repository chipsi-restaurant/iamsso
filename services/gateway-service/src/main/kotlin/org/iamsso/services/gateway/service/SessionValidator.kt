package org.iamsso.services.gateway.service

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Checks that a session referenced by JWT `sid` claim is still alive in session-service.
 *
 * Results are cached in-memory for 5 seconds — amortizes per-request calls from an SPA
 * while keeping logout propagation visually immediate across devices.
 */
@Component
class SessionValidator(
    webClientBuilder: WebClient.Builder,
    @Value("\${app.services.session-url}") private val sessionUrl: String,
) {

    private val webClient: WebClient = webClientBuilder.baseUrl(sessionUrl).build()

    private val aliveCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(5))
        .maximumSize(10_000)
        .build<String, Boolean>()

    fun isAlive(sessionId: String): Mono<Boolean> {
        if (sessionId.isBlank()) return Mono.just(false)
        aliveCache.getIfPresent(sessionId)?.let { return Mono.just(it) }
        return webClient.get()
            .uri("/api/v1/sessions/{id}", sessionId)
            .retrieve()
            .toBodilessEntity()
            .map { true }
            .doOnNext { aliveCache.put(sessionId, true) }
            .onErrorResume(WebClientResponseException.NotFound::class.java) {
                aliveCache.put(sessionId, false)
                Mono.just(false)
            }
            // session-service unreachable — fail-open to avoid cascading outage.
            .onErrorResume { Mono.just(true) }
    }

    fun invalidate(sessionId: String) {
        aliveCache.invalidate(sessionId)
    }
}
