package org.iamsso.services.gateway.filter

import org.iamsso.services.gateway.config.AppProperties
import org.iamsso.services.gateway.service.SessionValidator
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationFilter(
    private val jwtDecoder: ReactiveJwtDecoder,
    private val appProperties: AppProperties,
    private val sessionValidator: SessionValidator,
) : GlobalFilter, Ordered {

    private val pathMatcher = AntPathMatcher()

    override fun getOrder() = 1

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.uri.path

        if (isPublicPath(path)) return chain.filter(exchange)

        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid token")
        }

        val token = authHeader.substring(7)
        return jwtDecoder.decode(token)
            .flatMap { jwt ->
                val userId = jwt.subject ?: ""
                val role = jwt.getClaimAsString("role") ?: "user"
                val sid = jwt.getClaimAsString("sid") ?: ""
                val attributes = exchange.attributes
                attributes["jwt.sub"] = userId
                attributes["jwt.role"] = role
                attributes["jwt.scope"] = jwt.getClaimAsString("scope") ?: ""
                attributes["jwt.sid"] = sid
                val email = jwt.getClaimAsString("email") ?: ""
                val mutatedRequest = exchange.request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .header("X-User-Email", email)
                    .build()
                val mutatedExchange = exchange.mutate().request(mutatedRequest).build()

                // If token is tied to an SSO session, verify the session is still alive.
                // This enables immediate effect of logout/logout-all across devices, even
                // while access tokens are still within their exp window.
                if (sid.isBlank()) {
                    chain.filter(mutatedExchange)
                } else {
                    sessionValidator.isAlive(sid).flatMap { alive ->
                        if (alive) chain.filter(mutatedExchange)
                        else unauthorized(exchange, "Session revoked")
                    }
                }
            }
            .onErrorResume { e ->
                unauthorized(exchange, "Failed to validate the token")
            }
    }

    private fun isPublicPath(path: String): Boolean =
        appProperties.publicPaths.any { pathMatcher.match(it, path) }

    private fun unauthorized(exchange: ServerWebExchange, message: String): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.set(HttpHeaders.CONTENT_TYPE, "application/json")
        val body = """{"code":"UNAUTHORIZED","message":"$message"}"""
        val buffer = exchange.response.bufferFactory().wrap(body.toByteArray())
        return exchange.response.writeWith(Mono.just(buffer))
    }
}
