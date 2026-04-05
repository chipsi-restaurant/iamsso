package org.iamsso.services.authservice.service

import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TokenFamilyServiceTest {

    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var setOps: SetOperations<String, String>
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var service: RedisTokenFamilyService

    @BeforeEach
    fun setUp() {
        whenever(redis.opsForSet()).thenReturn(setOps)
        service = RedisTokenFamilyService(redis, refreshTokenRepository)
    }

    @Test
    fun `initFamily adds hash to set and sets expiry`() {
        val familyId = UUID.randomUUID()
        service.initFamily(familyId, "hash1", Duration.ofSeconds(60))
        verify(setOps).add("auth:family:$familyId", "hash1")
        verify(redis).expire("auth:family:$familyId", Duration.ofSeconds(60))
    }

    @Test
    fun `addToFamily adds hash to existing set`() {
        val familyId = UUID.randomUUID()
        service.addToFamily(familyId, "hash2")
        verify(setOps).add("auth:family:$familyId", "hash2")
    }

    @Test
    fun `revokeFamily calls revokeByTokenHash for each member and deletes key`() {
        val familyId = UUID.randomUUID()
        val key = "auth:family:$familyId"
        whenever(setOps.members(key)).thenReturn(setOf("hash1", "hash2"))
        service.revokeFamily(familyId)
        verify(refreshTokenRepository).revokeByTokenHash("hash1")
        verify(refreshTokenRepository).revokeByTokenHash("hash2")
        verify(redis).delete(key)
    }

    @Test
    fun `revokeFamily does nothing when family not in Redis`() {
        val familyId = UUID.randomUUID()
        whenever(setOps.members("auth:family:$familyId")).thenReturn(null)
        service.revokeFamily(familyId)
        verify(refreshTokenRepository, never()).revokeByTokenHash(org.mockito.kotlin.any())
    }
}
