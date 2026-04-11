package org.iamsso.services.auditservice.controller

import org.iamsso.services.auditservice.service.AuditService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(private val auditService: AuditService) {

    @GetMapping("/events")
    fun list(
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) eventSource: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ) = auditService.search(userId, eventType, eventSource, from, to, page, size)

    @GetMapping("/events/{id}")
    fun getById(@PathVariable id: UUID) =
        auditService.getById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found")

    @GetMapping("/stats")
    fun stats(
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
    ) = auditService.getStats(from, to)
}
