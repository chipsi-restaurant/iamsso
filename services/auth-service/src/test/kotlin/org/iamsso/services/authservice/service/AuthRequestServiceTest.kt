package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthRequestServiceTest {

    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>

    private lateinit var service: AuthRequestService
    private val mapper = ObjectMapper().registerKotlinModule().findAndRegisterModules()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(redis.opsForValue()).thenReturn(valueOps)
        service = AuthRequestService(redis, mapper)
    }

    @Test
    fun `save stores AuthRequest in Redis`() {
        val req = AuthRequest(
            authRequestId = "req-1",
            clientId = "client-1",
            redirectUri = "https://app.example.com/callback",
            scope = "openid profile",
            state = "xyz",
            codeChallenge = "abc123",
            codeChallengeMethod = "S256",
            nonce = null,
        )
        service.save(req, ttlSeconds = 300)
        verify(valueOps).set(eq("auth:request:req-1"), any<String>(), any<java.time.Duration>())
    }

    @Test
    fun `get returns null for missing key`() {
        whenever(valueOps.getAndDelete("auth:request:missing")).thenReturn(null)
        assertNull(service.getAndDelete("missing"))
    }
}
