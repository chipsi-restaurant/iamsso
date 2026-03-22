package org.iamsso.services.authservice.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.iamsso.services.authservice.config.AppProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

data class AuthorizationCodeData(
    val code: String,
    val clientId: String,
    val userId: UUID,
    val redirectUri: String,
    val scopes: List<String>,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val nonce: String? = null,
    val sessionId: String? = null,
)

@Repository
class AuthorizationCodeStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val props: AppProperties,
) {
    private val prefix = "auth:code:"

    fun save(data: AuthorizationCodeData) {
        val json = objectMapper.writeValueAsString(data)
        redis.opsForValue().set(
            "$prefix${data.code}",
            json,
            Duration.ofSeconds(props.authorizationCode.ttlSeconds),
        )
    }

    fun consume(code: String): AuthorizationCodeData? {
        val key = "$prefix$code"
        val json = redis.opsForValue().getAndDelete(key) ?: return null
        return objectMapper.readValue(json, AuthorizationCodeData::class.java)
    }
}