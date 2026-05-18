package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.service.SessionServiceClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/api/v1/sessions")
class LogoutController(
    private val sessionServiceClient: SessionServiceClient,
) {

    @PostMapping("/logout", consumes = ["application/x-www-form-urlencoded", "application/json", "*/*"])
    fun logoutPost(
        @RequestParam("post_logout_redirect_uri", required = false) postLogoutUri: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = doLogout(request, response, postLogoutUri)

    @GetMapping("/logout")
    fun logoutGet(
        @RequestParam("post_logout_redirect_uri", required = false) postLogoutUri: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = doLogout(request, response, postLogoutUri)

    @PostMapping("/logout-all", consumes = ["application/x-www-form-urlencoded", "application/json", "*/*"])
    fun logoutAll(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val sessionId = request.cookies?.find { it.name == "SSO_SESSION" }?.value
        if (sessionId != null) {
            val session = sessionServiceClient.get(sessionId)
            if (session != null) sessionServiceClient.deleteAllForUser(session.userId)
        }
        clearCookie(response)
    }

    private fun doLogout(request: HttpServletRequest, response: HttpServletResponse, postLogoutUri: String?) {
        val sessionId = request.cookies?.find { it.name == "SSO_SESSION" }?.value
        if (sessionId != null) {
            // session-service публикует session.destroyed → auth-service SessionEventConsumer
            // отзовёт refresh-токены автоматически
            sessionServiceClient.delete(sessionId)
        }
        clearCookie(response)
        if (postLogoutUri != null) response.sendRedirect(postLogoutUri)
    }

    private fun clearCookie(response: HttpServletResponse) {
        response.addCookie(Cookie("SSO_SESSION", "").apply {
            isHttpOnly = true
            path = "/"
            maxAge = 0
        })
    }
}
