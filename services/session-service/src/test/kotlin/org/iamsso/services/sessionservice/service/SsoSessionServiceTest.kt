package org.iamsso.services.sessionservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.sessionservice.config.AppProperties
import org.iamsso.services.sessionservice.model.SsoSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SsoSessionServiceTest {

    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>
    @Mock lateinit var setOps: SetOperations<String, String>
    @Mock lateinit var events: EventPublisher

    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
    }
    private lateinit var service: SsoSessionService

    @BeforeEach
    fun setUp() {
        whenever(redis.opsForValue()).thenReturn(valueOps)
        whenever(redis.opsForSet()).thenReturn(setOps)
        val props = AppProperties(ssoSession = AppProperties.SsoSessionProps(ttlSeconds = 3600))
        service = SsoSessionService(redis, mapper, props, events)
    }

    @Test
    fun `create saves session to Redis, adds to user index, publishes event`() {
        val userId = UUID.randomUUID()
        val session = service.create(userId, firstClientId = "client-1")

        assertEquals(userId, session.userId)
        assertEquals(listOf("client-1"), session.clientIds)

        // Stored in Redis with prefix and TTL
        verify(valueOps).set(eq("sso:session:${session.sessionId}"), any<String>(), any<Duration>())
        // Indexed in user set with TTL
        verify(setOps).add("sso:user:$userId", session.sessionId)
        verify(redis).expire(eq("sso:user:$userId"), any<Duration>())
        // Event published
        verify(events).sessionCreated(session.sessionId, userId, "client-1")
    }

    @Test
    fun `get returns null when Redis has no value`() {
        whenever(valueOps.get("sso:session:missing")).thenReturn(null)
        assertNull(service.get("missing"))
    }

    @Test
    fun `get returns deserialized session when Redis has value`() {
        val now = Instant.now()
        val userId = UUID.randomUUID()
        val session = SsoSession(
            sessionId = "abc",
            userId = userId,
            clientIds = mutableListOf("client-1"),
            createdAt = now,
            lastActivityAt = now,
            expiresAt = now.plusSeconds(3600),
        )
        whenever(valueOps.get("sso:session:abc")).thenReturn(mapper.writeValueAsString(session))

        val result = service.get("abc")

        assertNotNull(result)
        assertEquals("abc", result.sessionId)
        assertEquals(userId, result.userId)
        assertEquals(listOf("client-1"), result.clientIds)
    }

    @Test
    fun `updateActivity saves session with new lastActivityAt`() {
        val original = Instant.now().minusSeconds(120)
        val userId = UUID.randomUUID()
        val session = SsoSession(
            sessionId = "abc",
            userId = userId,
            clientIds = mutableListOf("client-1"),
            createdAt = original,
            lastActivityAt = original,
            expiresAt = Instant.now().plusSeconds(3600),
        )
        whenever(valueOps.get("sso:session:abc")).thenReturn(mapper.writeValueAsString(session))

        service.updateActivity("abc")

        val jsonCaptor = argumentCaptor<String>()
        verify(valueOps).set(eq("sso:session:abc"), jsonCaptor.capture(), any<Duration>())
        val saved = mapper.readValue(jsonCaptor.firstValue, SsoSession::class.java)
        assertTrue(saved.lastActivityAt.isAfter(original))
    }

    @Test
    fun `addClient appends new clientId without duplicates`() {
        val now = Instant.now()
        val userId = UUID.randomUUID()
        val session = SsoSession(
            sessionId = "abc",
            userId = userId,
            clientIds = mutableListOf("client-1"),
            createdAt = now,
            lastActivityAt = now,
            expiresAt = now.plusSeconds(3600),
        )
        whenever(valueOps.get("sso:session:abc")).thenReturn(mapper.writeValueAsString(session))

        // First addClient - new client should be appended
        service.addClient("abc", "client-2")
        val jsonCaptor = argumentCaptor<String>()
        verify(valueOps).set(eq("sso:session:abc"), jsonCaptor.capture(), any<Duration>())
        val saved = mapper.readValue(jsonCaptor.firstValue, SsoSession::class.java)
        assertEquals(listOf("client-1", "client-2"), saved.clientIds)

        // Now simulate that the saved session is what's returned by Redis
        whenever(valueOps.get("sso:session:abc")).thenReturn(mapper.writeValueAsString(saved))

        // Adding the same client again should NOT duplicate
        service.addClient("abc", "client-2")
        val jsonCaptor2 = argumentCaptor<String>()
        verify(valueOps, org.mockito.kotlin.times(2)).set(eq("sso:session:abc"), jsonCaptor2.capture(), any<Duration>())
        val savedAgain = mapper.readValue(jsonCaptor2.lastValue, SsoSession::class.java)
        assertEquals(listOf("client-1", "client-2"), savedAgain.clientIds)
    }

    @Test
    fun `delete removes session from Redis, updates user index, publishes destroyed event`() {
        val now = Instant.now()
        val userId = UUID.randomUUID()
        val session = SsoSession(
            sessionId = "abc",
            userId = userId,
            clientIds = mutableListOf("client-1"),
            createdAt = now,
            lastActivityAt = now,
            expiresAt = now.plusSeconds(3600),
        )
        whenever(valueOps.get("sso:session:abc")).thenReturn(mapper.writeValueAsString(session))

        service.delete("abc")

        verify(redis).delete("sso:session:abc")
        verify(setOps).remove("sso:user:$userId", "abc")
        verify(events).sessionDestroyed("abc", userId, "logout")
        verify(events, never()).sessionCreated(any(), any(), any())
    }
}
