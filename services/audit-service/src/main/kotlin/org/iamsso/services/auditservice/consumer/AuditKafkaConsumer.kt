package org.iamsso.services.auditservice.consumer

import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.services.auditservice.repository.AuditEventRepository
import org.iamsso.services.auditservice.service.AuditEventMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AuditKafkaConsumer(
    private val repo: AuditEventRepository,
    private val mapper: AuditEventMapper,
) {
    private val log = LoggerFactory.getLogger(AuditKafkaConsumer::class.java)

    @KafkaListener(
        topics = [
            KafkaTopics.USER_EVENTS,
            KafkaTopics.CREDENTIALS_EVENTS,
            KafkaTopics.MFA_EVENTS,
            KafkaTopics.AUTH_EVENTS,
            KafkaTopics.SESSION_EVENTS,
            KafkaTopics.POLICY_EVENTS,
        ],
        groupId = "audit-service",
    )
    @Transactional
    fun onEvent(message: String) {
        try {
            val entity = mapper.fromJson(message)
            if (entity != null) {
                repo.save(entity)
                log.debug("Persisted audit event type={} source={}", entity.eventType, entity.eventSource)
            } else {
                log.warn("Could not parse audit event: {}", message.take(200))
            }
        } catch (e: Exception) {
            log.error("Failed to process audit event: {}", e.message, e)
        }
    }
}
