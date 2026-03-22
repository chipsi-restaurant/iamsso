package org.iamsso.contracts.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthEventsTest {
    private val mapper = ObjectMapper().registerKotlinModule()
        .findAndRegisterModules()

    @Test
    fun `LoginSuccessEvent serializes with correct eventType`() {
        val event = LoginSuccessEvent(
            userId = UUID.randomUUID(),
            clientId = "test-client",
            sessionId = UUID.randomUUID().toString(),
        )
        val json = mapper.writeValueAsString(event)
        assertTrue(json.contains("\"eventType\":\"auth.login-success\""))
    }

    @Test
    fun `DomainEvent deserializes LoginSuccessEvent by eventType`() {
        val event = LoginSuccessEvent(
            userId = UUID.randomUUID(),
            clientId = "test-client",
            sessionId = UUID.randomUUID().toString(),
        )
        val json = mapper.writeValueAsString(event)
        val deserialized = mapper.readValue(json, DomainEvent::class.java)
        assertTrue(deserialized is LoginSuccessEvent)
        assertEquals(event.clientId, (deserialized as LoginSuccessEvent).clientId)
    }
}
