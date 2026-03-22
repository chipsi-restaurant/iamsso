package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.iamsso.services.authservice.config.AppProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedisSsoSessionServiceTest {

    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>
    @Mock lateinit var setOps: SetOperations<String, String>

    private lateinit var service: RedisSsoSessionService
    private val mapper = ObjectMapper().registerKotlinModule().findAndRegisterModules()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(redis.opsForValue()).thenReturn(valueOps)
        whenever(redis.opsForSet()).thenReturn(setOps)
        val props = AppProperties()
        service = RedisSsoSessionService(redis, mapper, props)
    }

    @Test
    fun `create stores session and returns it with firstClientId in list`() {
        val userId = UUID.randomUUID()
        val session = service.create(userId, firstClientId = "client-1")
        assertEquals(userId, session.userId)
        assertEquals(listOf("client-1"), session.clientIds)
        verify(valueOps).set(any<String>(), any<String>(), any<java.time.Duration>())
    }

    @Test
    fun `get returns null for missing session`() {
        whenever(valueOps.get(any())).thenReturn(null)
        val result = service.get("missing-id")
        assertNull(result)
    }
}
