package org.iamsso.services.sessionservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.sessionservice.dto.AddClientRequest
import org.iamsso.services.sessionservice.dto.CreateSessionRequest
import org.iamsso.services.sessionservice.dto.SessionResponse
import org.iamsso.services.sessionservice.model.SsoSession
import org.iamsso.services.sessionservice.service.SsoSessionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController(private val service: SsoSessionService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: CreateSessionRequest): SessionResponse =
        service.create(req.userId, req.clientId).toResponse()

    @GetMapping("/{sessionId}")
    fun get(@PathVariable sessionId: String): SessionResponse =
        service.get(sessionId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")

    @PostMapping("/{sessionId}/activity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateActivity(@PathVariable sessionId: String) {
        service.updateActivity(sessionId)
    }

    @PostMapping("/{sessionId}/clients")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addClient(@PathVariable sessionId: String, @RequestBody req: AddClientRequest) {
        service.addClient(sessionId, req.clientId)
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable sessionId: String) {
        service.delete(sessionId, "logout")
    }

    @DeleteMapping("/user/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAllForUser(@PathVariable userId: UUID) {
        service.deleteAllForUser(userId)
    }

    @GetMapping("/current")
    fun current(request: HttpServletRequest): ResponseEntity<SessionResponse> {
        val sessionId = request.cookies?.find { it.name == "SSO_SESSION" }?.value
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        val session = service.get(sessionId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(session.toResponse())
    }

    private fun SsoSession.toResponse() = SessionResponse(
        sessionId = sessionId,
        userId = userId,
        clientIds = clientIds.toList(),
        createdAt = createdAt,
        lastActivityAt = lastActivityAt,
        expiresAt = expiresAt,
    )
}
