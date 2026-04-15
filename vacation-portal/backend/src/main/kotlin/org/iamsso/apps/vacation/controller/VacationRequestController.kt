package org.iamsso.apps.vacation.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.iamsso.apps.vacation.entity.VacationRequestEntity
import org.iamsso.apps.vacation.entity.VacationRequestStatus
import org.iamsso.apps.vacation.exception.ForbiddenException
import org.iamsso.apps.vacation.service.VacationRequestService
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/vacation-requests")
class VacationRequestController(private val service: VacationRequestService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: CreateRequestBody, request: HttpServletRequest): VacationRequestResponse {
        val userId = requireUser(request)
        return VacationRequestResponse.from(service.create(userId, body))
    }

    @GetMapping("/my")
    fun my(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        request: HttpServletRequest,
    ): PagedResponse<VacationRequestResponse> {
        val userId = requireUser(request)
        return toPage(service.findMyRequests(userId, page, size))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID, request: HttpServletRequest): VacationRequestResponse {
        val userId = requireUser(request)
        val manager = isManager(request)
        return VacationRequestResponse.from(service.getById(id, userId, manager))
    }

    @GetMapping
    fun all(
        @RequestParam(required = false) status: VacationRequestStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        request: HttpServletRequest,
    ): PagedResponse<VacationRequestResponse> {
        requireUser(request)
        if (!isManager(request)) throw ForbiddenException("Manager role required")
        return toPage(service.findAll(status, page, size))
    }

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: UUID,
        @RequestBody(required = false) body: ReviewCommentBody?,
        request: HttpServletRequest,
    ): VacationRequestResponse {
        val userId = requireUser(request)
        if (!isManager(request)) throw ForbiddenException("Manager role required")
        return VacationRequestResponse.from(service.approve(id, userId, body?.comment))
    }

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: UUID,
        @RequestBody(required = false) body: ReviewCommentBody?,
        request: HttpServletRequest,
    ): VacationRequestResponse {
        val userId = requireUser(request)
        if (!isManager(request)) throw ForbiddenException("Manager role required")
        return VacationRequestResponse.from(service.reject(id, userId, body?.comment))
    }

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: UUID, request: HttpServletRequest): VacationRequestResponse {
        val userId = requireUser(request)
        return VacationRequestResponse.from(service.cancel(id, userId))
    }

    private fun requireUser(request: HttpServletRequest): UUID {
        val header = request.getHeader("X-User-Id")
            ?: throw ForbiddenException("Missing X-User-Id header")
        return runCatching { UUID.fromString(header) }
            .getOrElse { throw ForbiddenException("Invalid X-User-Id header") }
    }

    private fun isManager(request: HttpServletRequest): Boolean {
        val role = request.getHeader("X-User-Role")?.lowercase()
        return role == "moderator" || role == "admin"
    }

    private fun toPage(p: Page<VacationRequestEntity>) = PagedResponse(
        content = p.content.map { VacationRequestResponse.from(it) },
        page = p.number, size = p.size,
        totalElements = p.totalElements, totalPages = p.totalPages,
    )
}
