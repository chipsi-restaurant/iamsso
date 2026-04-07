package org.iamsso.contracts.events

import java.time.Instant
import java.util.UUID

data class PolicyCreatedEvent(
    val policyId: UUID,
    val name: String,
    val role: String,
    val action: String,
    val resourcePattern: String,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "policy.created"
}

data class PolicyUpdatedEvent(
    val policyId: UUID,
    val name: String,
    val changedFields: List<String>,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "policy.updated"
}

data class PolicyDeletedEvent(
    val policyId: UUID,
    val name: String,
    override val timestamp: Instant = Instant.now(),
) : DomainEvent {
    override val eventType: String = "policy.deleted"
}
