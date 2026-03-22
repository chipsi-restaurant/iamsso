package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

data class AuthRequest(
    val authRequestId: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String?,
    val state: String?,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val nonce: String?,
)

@Service
class AuthRequestService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val prefix = "auth:request:"

    fun save(request: AuthRequest, ttlSeconds: Long) {
        val json = objectMapper.writeValueAsString(request)
        redis.opsForValue().set("$prefix${request.authRequestId}", json, Duration.ofSeconds(ttlSeconds))
    }

    fun getAndDelete(authRequestId: String): AuthRequest? {
        val json = redis.opsForValue().getAndDelete("$prefix$authRequestId") ?: return null
        return objectMapper.readValue(json, AuthRequest::class.java)
    }
}
