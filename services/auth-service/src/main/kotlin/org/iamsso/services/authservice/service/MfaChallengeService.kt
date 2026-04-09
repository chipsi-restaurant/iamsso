package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

data class MfaChallenge(
    val challengeId: String = UUID.randomUUID().toString(),
    val userId: UUID,
    val authRequest: AuthRequest,
    val factorTypes: List<String>,
)

@Service
class MfaChallengeService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val prefix = "mfa:challenge:"

    fun save(challenge: MfaChallenge, ttlSeconds: Long) {
        val json = objectMapper.writeValueAsString(challenge)
        redis.opsForValue().set("$prefix${challenge.challengeId}", json, Duration.ofSeconds(ttlSeconds))
    }

    fun getAndDelete(challengeId: String): MfaChallenge? {
        val json = redis.opsForValue().getAndDelete("$prefix$challengeId") ?: return null
        return objectMapper.readValue(json, MfaChallenge::class.java)
    }

    fun get(challengeId: String): MfaChallenge? {
        val json = redis.opsForValue().get("$prefix$challengeId") ?: return null
        return objectMapper.readValue(json, MfaChallenge::class.java)
    }
}
