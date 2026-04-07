package org.iamsso.services.gateway.filter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.iamsso.services.gateway.config.AppProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(okhttp3.ExperimentalOkHttpApi::class)
class PolicyEnforcementFilterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var filter: PolicyEnforcementFilter
    private lateinit var chain: GatewayFilterChain
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val evaluateUrl = mockWebServer.url("/api/v1/policy/evaluate").toString()
        val appProperties = AppProperties(
            publicPaths = listOf("/oauth2/**"),
            policyService = AppProperties.PolicyServiceProps(evaluateUrl = evaluateUrl),
        )

        filter = PolicyEnforcementFilter(appProperties, WebClient.builder())
        chain = mock()
        whenever(chain.filter(any())).thenReturn(Mono.empty())
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse.Builder()
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    @Test
    fun `public path skips policy check`() {
        val request = MockServerHttpRequest.get("/oauth2/token").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(chain).filter(exchange)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `allowed response continues chain`() {
        mockWebServer.enqueue(jsonResponse("""{"allowed":true,"reason":""}"""))

        val request = MockServerHttpRequest.get("/api/v1/users").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["jwt.sub"] = "user-123"
        exchange.attributes["jwt.role"] = "admin"

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(chain).filter(exchange)
        assertNull(exchange.response.statusCode)
    }

    @Test
    fun `denied response returns 403`() {
        mockWebServer.enqueue(jsonResponse("""{"allowed":false,"reason":"insufficient permissions"}"""))

        val request = MockServerHttpRequest.delete("/api/v1/users/42").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["jwt.sub"] = "user-456"
        exchange.attributes["jwt.role"] = "user"

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(chain, never()).filter(any())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `GET maps to READ action and resource extracted correctly`() {
        mockWebServer.enqueue(jsonResponse("""{"allowed":true,"reason":""}"""))

        val request = MockServerHttpRequest.get("/api/v1/users/99").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["jwt.sub"] = "user-789"
        exchange.attributes["jwt.role"] = "admin"

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        val recorded = mockWebServer.takeRequest()
        val body: Map<String, Any> = mapper.readValue(recorded.body.readUtf8())

        assertEquals("READ", body["action"])
        assertEquals("users/99", body["resource"])

        @Suppress("UNCHECKED_CAST")
        val subject = body["subject"] as Map<String, String>
        assertEquals("user-789", subject["userId"])
        assertEquals("admin", subject["role"])
    }

    @Test
    fun `policy service unavailable returns 503`() {
        mockWebServer.shutdown()

        val request = MockServerHttpRequest.get("/api/v1/protected").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes["jwt.sub"] = "user-000"
        exchange.attributes["jwt.role"] = "user"

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(chain, never()).filter(any())
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
    }
}
