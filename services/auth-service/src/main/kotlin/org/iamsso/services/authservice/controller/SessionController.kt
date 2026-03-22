package org.iamsso.services.authservice.controller

import org.iamsso.contracts.auth.model.SessionResponse
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.SsoSession
import org.iamsso.services.authservice.service.SsoSessionService
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController(
    private val ssoSessionService: SsoSessionService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authEventPublisher: AuthEventPublisher,
) {

    @GetMapping("/current")
    fun getCurrent(request: HttpServletRequest): SessionResponse {
        val session = resolveSession(request)
        return session.toResponse()
    }

    @PostMapping("/logout", consumes = ["application/x-www-form-urlencoded", "application/json", "*/*"])
    @Transactional
    fun logout(
        @RequestParam("post_logout_redirect_uri", required = false) postLogoutUri: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val session = resolveSession(request)
        refreshTokenRepository.revokeAllBySessionId(UUID.fromString(session.sessionId))
        ssoSessionService.delete(session.sessionId)
        authEventPublisher.publishSessionDestroyed(session.sessionId, session.userId, "logout")
        clearCookie(response)
        if (postLogoutUri != null) {
            response.sendRedirect(postLogoutUri)
        }
    }

    @PostMapping("/logout-all")
    @Transactional
    fun logoutAll(request: HttpServletRequest, response: HttpServletResponse) {
        val session = resolveSession(request)
        refreshTokenRepository.revokeAllByUserId(session.userId)
        ssoSessionService.deleteAllForUser(session.userId)
        authEventPublisher.publishSessionDestroyed(session.sessionId, session.userId, "logout-all")
        clearCookie(response)
    }

    private fun resolveSession(request: HttpServletRequest): SsoSession =
        request.cookies?.find { it.name == "SSO_SESSION" }?.value
            ?.let { ssoSessionService.get(it) }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No active session")

    private fun clearCookie(response: HttpServletResponse) {
        response.addCookie(Cookie("SSO_SESSION", "").apply {
            isHttpOnly = true
            path = "/"
            maxAge = 0
        })
    }

    private fun SsoSession.toResponse() = SessionResponse(
        sessionId = sessionId,
        userId = userId,
        clientIds = clientIds,
        createdAt = createdAt,
        lastActivityAt = lastActivityAt,
        expiresAt = expiresAt,
    )
}
