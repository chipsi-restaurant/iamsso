package org.iamsso.services.gateway.filter

import org.iamsso.services.gateway.config.AppProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class JwtAuthenticationFilterTest {

    private lateinit var jwtDecoder: ReactiveJwtDecoder
    private lateinit var chain: GatewayFilterChain
    private lateinit var filter: JwtAuthenticationFilter

    private val appProperties = AppProperties(
        publicPaths = listOf("/oauth2/**", "/.well-known/**", "/login", "/actuator/**"),
    )

    @BeforeEach
    fun setUp() {
        jwtDecoder = mock()
        chain = mock()
        filter = JwtAuthenticationFilter(jwtDecoder, appProperties)
        whenever(chain.filter(any<ServerWebExchange>())).thenReturn(Mono.empty())
    }

    @Test
    fun `public path skips JWT validation`() {
        val request = MockServerHttpRequest.get("/oauth2/token").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(chain).filter(exchange)
        verify(jwtDecoder, never()).decode(any())
    }

    @Test
    fun `missing Authorization header returns 401`() {
        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
        verify(chain, never()).filter(any())
    }

    @Test
    fun `non-Bearer Authorization header returns 401`() {
        val request = MockServerHttpRequest.get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
        verify(chain, never()).filter(any())
    }

    @Test
    fun `valid JWT sets attributes and continues chain`() {
        val jwt = Jwt.withTokenValue("valid-token")
            .header("alg", "RS256")
            .subject("user-123")
            .claim("role", "admin")
            .claim("scope", "openid profile")
            .claim("sid", "session-456")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        whenever(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt))

        val request = MockServerHttpRequest.get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        assertEquals("user-123", exchange.attributes["jwt.sub"])
        assertEquals("admin", exchange.attributes["jwt.role"])
        assertEquals("openid profile", exchange.attributes["jwt.scope"])
        assertEquals("session-456", exchange.attributes["jwt.sid"])
        verify(chain).filter(exchange)
    }

    @Test
    fun `expired or invalid JWT returns 401`() {
        whenever(jwtDecoder.decode("bad-token"))
            .thenReturn(Mono.error(JwtException("Token expired")))

        val request = MockServerHttpRequest.get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
            .build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
        verify(chain, never()).filter(any())
    }
}
