package org.iamsso.services.authservice.service

import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

interface TokenFamilyService {
    fun initFamily(familyId: UUID, tokenHash: String, ttl: Duration)
    fun addToFamily(familyId: UUID, tokenHash: String)
    fun revokeFamily(familyId: UUID)
}

@Service
class RedisTokenFamilyService(
    private val redis: StringRedisTemplate,
    private val refreshTokenRepository: RefreshTokenRepository,
) : TokenFamilyService {

    private val prefix = "auth:family:"

    override fun initFamily(familyId: UUID, tokenHash: String, ttl: Duration) {
        val key = "$prefix$familyId"
        redis.opsForSet().add(key, tokenHash)
        redis.expire(key, ttl)
    }

    override fun addToFamily(familyId: UUID, tokenHash: String) {
        redis.opsForSet().add("$prefix$familyId", tokenHash)
    }

    override fun revokeFamily(familyId: UUID) {
        val key = "$prefix$familyId"
        val hashes = redis.opsForSet().members(key) ?: return
        hashes.forEach { refreshTokenRepository.revokeByTokenHash(it) }
        redis.delete(key)
    }
}
