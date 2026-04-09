package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.repository.AuthorizationCodeData
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.service.*
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID

@Controller
class MfaChallengeController(
    private val mfaChallengeService: MfaChallengeService,
    private val mfaServiceClient: MfaServiceClient,
    private val ssoSessionService: SsoSessionService,
    private val authCodeStore: AuthorizationCodeStore,
    private val authEventPublisher: AuthEventPublisher,
    private val props: AppProperties,
) {
    @GetMapping("/mfa-challenge")
    fun showChallenge(
        @RequestParam("challenge_id") challengeId: String,
        @RequestParam("error", required = false) error: String?,
        model: Model,
    ): String {
        val challenge = mfaChallengeService.get(challengeId)
        if (challenge == null) {
            model.addAttribute("error", "mfa_expired")
            model.addAttribute("challengeId", "")
            model.addAttribute("factorTypes", emptyList<String>())
            return "mfa-challenge"
        }
        model.addAttribute("challengeId", challengeId)
        model.addAttribute("factorTypes", challenge.factorTypes)
        model.addAttribute("error", error)
        return "mfa-challenge"
    }

    @PostMapping("/mfa-challenge")
    fun verifyChallenge(
        @RequestParam("challenge_id") challengeId: String,
        @RequestParam("mfa_code") mfaCode: String,
        response: HttpServletResponse,
    ): RedirectView {
        val challenge = mfaChallengeService.getAndDelete(challengeId)
            ?: return RedirectView("/login?error=mfa_expired")

        val result = mfaServiceClient.verify(challenge.userId, mfaCode)
        if (result == null || !result.valid) {
            val newChallenge = challenge.copy(challengeId = UUID.randomUUID().toString())
            mfaChallengeService.save(newChallenge, props.mfaChallenge.ttlSeconds)
            return RedirectView("/mfa-challenge?challenge_id=${newChallenge.challengeId}&error=invalid_code")
        }

        // MFA passed — create session and auth code
        val authRequest = challenge.authRequest
        val session = ssoSessionService.create(challenge.userId, authRequest.clientId)
        response.addCookie(Cookie("SSO_SESSION", session.sessionId).apply {
            isHttpOnly = true
            path = "/"
            maxAge = props.ssoSession.ttlSeconds.toInt()
        })

        val code = UUID.randomUUID().toString()
        authCodeStore.save(AuthorizationCodeData(
            code = code,
            clientId = authRequest.clientId,
            userId = challenge.userId,
            redirectUri = authRequest.redirectUri,
            scopes = authRequest.scope?.split(" ") ?: emptyList(),
            codeChallenge = authRequest.codeChallenge,
            codeChallengeMethod = authRequest.codeChallengeMethod,
            nonce = authRequest.nonce,
            sessionId = session.sessionId,
        ))

        authEventPublisher.publishLoginSuccess(challenge.userId, authRequest.clientId, session.sessionId)
        authEventPublisher.publishSessionCreated(session.sessionId, challenge.userId, authRequest.clientId)

        val location = buildString {
            append("${authRequest.redirectUri}?code=$code")
            if (authRequest.state != null) append("&state=${authRequest.state}")
        }
        return RedirectView(location)
    }
}
