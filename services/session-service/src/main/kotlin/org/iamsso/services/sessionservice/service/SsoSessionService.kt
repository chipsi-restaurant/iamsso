package org.iamsso.services.sessionservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.iamsso.services.sessionservice.config.AppProperties
import org.iamsso.services.sessionservice.model.SsoSession
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class SsoSessionService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val props: AppProperties,
    private val events: EventPublisher,
) {
    private val ttlSeconds get() = props.ssoSession.ttlSeconds
    private val prefix = "sso:session:"
    private val userIndex = "sso:user:"

    fun create(userId: UUID, firstClientId: String): SsoSession {
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
        events.sessionCreated(session.sessionId, userId, firstClientId)
        return session
    }

    fun get(sessionId: String): SsoSession? {
        val json = redis.opsForValue().get("$prefix$sessionId") ?: return null
        return objectMapper.readValue(json, SsoSession::class.java)
    }

    fun findAllForUser(userId: UUID): List<SsoSession> {
        val sessionIds = redis.opsForSet().members("$userIndex$userId") ?: return emptyList()
        return sessionIds.mapNotNull { get(it) }
            .sortedByDescending { it.lastActivityAt }
    }

    fun addClient(sessionId: String, clientId: String) {
        val session = get(sessionId) ?: return
        if (clientId !in session.clientIds) session.clientIds.add(clientId)
        save(session.copy(lastActivityAt = Instant.now()))
    }

    fun updateActivity(sessionId: String) {
        val session = get(sessionId) ?: return
        save(session.copy(lastActivityAt = Instant.now()))
    }

    fun delete(sessionId: String, reason: String = "logout") {
        val session = get(sessionId) ?: return
        redis.delete("$prefix$sessionId")
        redis.opsForSet().remove("$userIndex${session.userId}", sessionId)
        events.sessionDestroyed(sessionId, session.userId, reason)
    }

    fun deleteAllForUser(userId: UUID) {
        val sessionIds = redis.opsForSet().members("$userIndex$userId") ?: return
        sessionIds.forEach { sid ->
            redis.delete("$prefix$sid")
            events.sessionDestroyed(sid, userId, "logout-all")
        }
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
