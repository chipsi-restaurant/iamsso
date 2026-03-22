package org.iamsso.services.authservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.iamsso.services.authservice.config.AppProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RedisSsoSessionService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val props: AppProperties,
) : SsoSessionService {

    private val ttlSeconds get() = props.ssoSession.ttlSeconds
    private val prefix = "sso:session:"
    private val userIndex = "sso:user:"  // Set of sessionIds per userId

    override fun create(userId: UUID, firstClientId: String): SsoSession {
        val now = Instant.now()
        val session = SsoSession(
            sessionId = UUID.randomUUID().toString(),
            userId = userId,
            clientIds = mutableListOf(firstClientId),
            createdAt = now,
            lastActivityAt = now,
            expiresAt = now.plusSeconds(ttlSeconds),
        )
        save(session)
        redis.opsForSet().add("$userIndex$userId", session.sessionId)
        redis.expire("$userIndex$userId", Duration.ofSeconds(ttlSeconds))
        return session
    }

    override fun get(sessionId: String): SsoSession? {
        val json = redis.opsForValue().get("$prefix$sessionId") ?: return null
        return objectMapper.readValue(json, SsoSession::class.java)
    }

    override fun addClient(sessionId: String, clientId: String) {
        val session = get(sessionId) ?: return
        if (clientId !in session.clientIds) session.clientIds.add(clientId)
        save(session.copy(lastActivityAt = Instant.now()))
    }

    override fun updateActivity(sessionId: String) {
        val session = get(sessionId) ?: return
        save(session.copy(lastActivityAt = Instant.now()))
    }

    override fun delete(sessionId: String) {
        val session = get(sessionId)
        redis.delete("$prefix$sessionId")
        if (session != null) {
            redis.opsForSet().remove("$userIndex${session.userId}", sessionId)
        }
    }

    override fun deleteAllForUser(userId: UUID) {
        val sessionIds = redis.opsForSet().members("$userIndex$userId") ?: return
        sessionIds.forEach { redis.delete("$prefix$it") }
        redis.delete("$userIndex$userId")
    }

    private fun save(session: SsoSession) {
        val json = objectMapper.writeValueAsString(session)
        val remaining = Duration.between(Instant.now(), session.expiresAt)
        if (!remaining.isNegative) {
            redis.opsForValue().set("$prefix${session.sessionId}", json, remaining)
        }
    }
}
