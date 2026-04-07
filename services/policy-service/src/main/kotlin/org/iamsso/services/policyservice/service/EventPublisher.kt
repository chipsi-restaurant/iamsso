package org.iamsso.services.policyservice.service

import org.iamsso.contracts.events.CloudEventEnvelope
import org.iamsso.contracts.events.DomainEvent
import org.iamsso.contracts.events.KafkaTopics
import org.iamsso.contracts.events.PolicyCreatedEvent
import org.iamsso.contracts.events.PolicyDeletedEvent
import org.iamsso.contracts.events.PolicyUpdatedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EventPublisher(private val kafka: KafkaTemplate<String, Any>) {

    fun policyCreated(policyId: UUID, name: String, role: String, action: String, resourcePattern: String) {
        send(policyId, PolicyCreatedEvent(policyId, name, role, action, resourcePattern))
    }

    fun policyUpdated(policyId: UUID, name: String, changedFields: List<String>) {
        send(policyId, PolicyUpdatedEvent(policyId, name, changedFields))
    }

    fun policyDeleted(policyId: UUID, name: String) {
        send(policyId, PolicyDeletedEvent(policyId, name))
    }

    private fun <T : DomainEvent> send(policyId: UUID, event: T) {
        kafka.send(
            KafkaTopics.POLICY_EVENTS,
            policyId.toString(),
            CloudEventEnvelope(source = "policy-service", type = event.eventType, data = event)
        )
    }
}
