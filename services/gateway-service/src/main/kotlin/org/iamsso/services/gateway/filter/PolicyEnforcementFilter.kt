package org.iamsso.services.gateway.filter

import org.iamsso.services.gateway.config.AppProperties
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class PolicyEnforcementFilter(
    private val appProperties: AppProperties,
    webClientBuilder: WebClient.Builder,
) : GlobalFilter, Ordered {

    private val pathMatcher = AntPathMatcher()
    private val webClient = webClientBuilder.build()

    override fun getOrder() = 2

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.uri.path

        if (isPublicPath(path)) return chain.filter(exchange)

        val userId = exchange.attributes["jwt.sub"] as? String ?: ""
        val role = exchange.attributes["jwt.role"] as? String ?: "user"
        val action = mapAction(exchange.request.method)
        val resource = extractResource(path)

        val requestBody = mapOf(
            "subject" to mapOf("userId" to userId, "role" to role),
            "action" to action,
            "resource" to resource,
        )

        return webClient.post()
            .uri(appProperties.policyService.evaluateUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(PolicyEvaluateResponse::class.java)
            .flatMap { response ->
                if (response.allowed) {
                    chain.filter(exchange)
                } else {
                    forbidden(exchange, response.reason)
                }
            }
            .onErrorResume { e ->
                serviceUnavailable(exchange)
            }
    }

    private fun isPublicPath(path: String): Boolean =
        appProperties.publicPaths.any { pathMatcher.match(it, path) }

    private fun mapAction(method: HttpMethod?): String = when (method) {
        HttpMethod.GET -> "READ"
        HttpMethod.POST -> "CREATE"
        HttpMethod.PUT, HttpMethod.PATCH -> "UPDATE"
        HttpMethod.DELETE -> "DELETE"
        else -> "READ"
    }

    private fun extractResource(path: String): String =
        path.removePrefix("/api/v1/").ifEmpty { path }

    private fun forbidden(exchange: ServerWebExchange, reason: String): Mono<Void> {
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        exchange.response.headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
        val body = """{"code":"ACCESS_DENIED","reason":"$reason"}"""
        val buffer = exchange.response.bufferFactory().wrap(body.toByteArray())
        return exchange.response.writeWith(Mono.just(buffer))
    }

    private fun serviceUnavailable(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
        exchange.response.headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
        val body = """{"code":"SERVICE_UNAVAILABLE","message":"Policy service unavailable"}"""
        val buffer = exchange.response.bufferFactory().wrap(body.toByteArray())
        return exchange.response.writeWith(Mono.just(buffer))
    }
}

data class PolicyEvaluateResponse(
    val allowed: Boolean = false,
    val policyId: String? = null,
    val reason: String = "",
)
