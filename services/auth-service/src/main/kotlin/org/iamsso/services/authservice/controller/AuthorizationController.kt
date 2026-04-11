package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.repository.AuthorizationCodeData
import org.iamsso.services.authservice.repository.AuthorizationCodeStore
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.AuthRequest
import org.iamsso.services.authservice.service.AuthRequestService
import org.iamsso.services.authservice.service.MfaChallenge
import org.iamsso.services.authservice.service.MfaChallengeService
import org.iamsso.services.authservice.service.MfaServiceClient
import org.iamsso.services.authservice.service.SessionServiceClient
import org.iamsso.services.authservice.service.UserServiceClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView
import java.util.UUID
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@RestController
class AuthorizationController(
    private val clientRepository: OAuthClientRepository,
    private val authRequestService: AuthRequestService,
    private val sessionServiceClient: SessionServiceClient,
    private val authCodeStore: AuthorizationCodeStore,
    private val userServiceClient: UserServiceClient,
    private val authEventPublisher: AuthEventPublisher,
    private val mfaChallengeService: MfaChallengeService,
    private val mfaServiceClient: MfaServiceClient,
    private val props: AppProperties,
) {

    @GetMapping("/oauth2/authorize")
    fun authorize(
        @RequestParam("response_type") responseType: String?,
        @RequestParam("client_id") clientId: String?,
        @RequestParam("redirect_uri") redirectUri: String?,
        @RequestParam("scope") scope: String?,
        @RequestParam("state") state: String?,
        @RequestParam("code_challenge") codeChallenge: String?,
        @RequestParam("code_challenge_method") codeChallengeMethod: String?,
        @RequestParam("nonce") nonce: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        if (clientId == null) return errorNoRedirect(response, "missing client_id")
        val client = clientRepository.findById(clientId).orElse(null)
            ?: return errorNoRedirect(response, "unknown client_id")
        if (redirectUri == null || redirectUri !in client.redirectUriList())
            return errorNoRedirect(response, "invalid redirect_uri")

        if (responseType != "code") return errorRedirect(redirectUri, "unsupported_response_type", state)
        if (codeChallenge == null || codeChallengeMethod != "S256")
            return errorRedirect(redirectUri, "invalid_request", state)

        val requestedScopes = scope?.split(" ") ?: emptyList()
        if (requestedScopes.any { it !in client.scopeList() })
            return errorRedirect(redirectUri, "invalid_scope", state)

        val sessionId = request.cookies?.find { it.name == "SSO_SESSION" }?.value
        if (sessionId != null) {
            val session = sessionServiceClient.get(sessionId)
            if (session != null) {
                sessionServiceClient.updateActivity(sessionId)
                sessionServiceClient.addClient(sessionId, clientId)
                val code = UUID.randomUUID().toString()
                authCodeStore.save(
                    AuthorizationCodeData(
                        code = code,
                        clientId = clientId,
                        userId = session.userId,
                        redirectUri = redirectUri,
                        scopes = requestedScopes,
                        codeChallenge = codeChallenge,
                        codeChallengeMethod = codeChallengeMethod,
                        nonce = nonce,
                        sessionId = sessionId,
                    )
                )
                val location = buildString {
                    append("$redirectUri?code=$code")
                    if (state != null) append("&state=$state")
                }
                return RedirectView(location)
            }
        }

        val authRequest = AuthRequest(
            authRequestId = UUID.randomUUID().toString(),
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            nonce = nonce,
        )
        authRequestService.save(authRequest, props.authorizationRequest.ttlSeconds)
        return RedirectView("/login?auth_request_id=${authRequest.authRequestId}")
    }

    @PostMapping("/oauth2/authorize/callback", consumes = ["application/x-www-form-urlencoded"])
    fun callback(
        @RequestParam("auth_request_id") authRequestId: String,
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): RedirectView {
        val loginPage = props.loginPage.url

        val authRequest = authRequestService.getAndDelete(authRequestId)
            ?: return errorNoRedirect(response, "invalid_request")

        val user = (if ("@" in username) userServiceClient.getByEmail(username)
                   else userServiceClient.getByUsername(username))
            ?: return run {
                val newId = resaveRequest(authRequest)
                authEventPublisher.publishLoginFailed(username, authRequest.clientId, "invalid_credentials")
                RedirectView("/login?auth_request_id=$newId&error=invalid_credentials")
            }

        if (user.status != "ACTIVE") {
            val newId = resaveRequest(authRequest)
            return RedirectView("/login?auth_request_id=$newId&error=account_disabled")
        }

        val creds = userServiceClient.verifyCredentials(user.id, password)
        if (creds == null || !creds.valid) {
            val newId = resaveRequest(authRequest)
            val reason = if (creds?.lockedUntil != null) "account_locked" else "invalid_credentials"
            authEventPublisher.publishLoginFailed(username, authRequest.clientId, reason)
            return RedirectView("/login?auth_request_id=$newId&error=$reason")
        }

        // Check if MFA is required
        val mfaStatus = mfaServiceClient.getStatus(user.id)
        if (mfaStatus != null && mfaStatus.mfaEnabled) {
            // Send OTP only if EMAIL_OTP is the only active factor (TOTP has priority)
            if ("EMAIL_OTP" in mfaStatus.activeFactors && "TOTP" !in mfaStatus.activeFactors && user.email != null) {
                mfaServiceClient.sendOtp(user.id, user.email!!)
            }
            val challenge = MfaChallenge(
                userId = user.id,
                authRequest = authRequest,
                factorTypes = mfaStatus.activeFactors,
            )
            mfaChallengeService.save(challenge, props.mfaChallenge.ttlSeconds)
            return RedirectView("/mfa-challenge?challenge_id=${challenge.challengeId}")
        }

        val session = sessionServiceClient.create(user.id, authRequest.clientId)
            ?: return errorNoRedirect(response, "session_creation_failed")
        val cookie = Cookie("SSO_SESSION", session.sessionId).apply {
            isHttpOnly = true
            path = "/"
            maxAge = props.ssoSession.ttlSeconds.toInt()
        }
        response.addCookie(cookie)

        val code = UUID.randomUUID().toString()
        authCodeStore.save(
            AuthorizationCodeData(
                code = code,
                clientId = authRequest.clientId,
                userId = user.id,
                redirectUri = authRequest.redirectUri,
                scopes = authRequest.scope?.split(" ") ?: emptyList(),
                codeChallenge = authRequest.codeChallenge,
                codeChallengeMethod = authRequest.codeChallengeMethod,
                nonce = authRequest.nonce,
                sessionId = session.sessionId,
            )
        )

        authEventPublisher.publishLoginSuccess(user.id, authRequest.clientId, session.sessionId)

        val location = buildString {
            append("${authRequest.redirectUri}?code=$code")
            if (authRequest.state != null) append("&state=${authRequest.state}")
        }
        return RedirectView(location)
    }

    private fun resaveRequest(req: AuthRequest): String {
        val newReq = req.copy(authRequestId = UUID.randomUUID().toString())
        authRequestService.save(newReq, props.authorizationRequest.ttlSeconds)
        return newReq.authRequestId
    }

    private fun errorNoRedirect(@Suppress("UNUSED_PARAMETER") response: HttpServletResponse, message: String): RedirectView {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    }

    private fun errorRedirect(redirectUri: String, error: String, state: String?): RedirectView {
        val location = buildString {
            append("$redirectUri?error=$error")
            if (state != null) append("&state=$state")
        }
        return RedirectView(location)
    }

    private fun errorResponse(redirectUri: String?, error: String, state: String?): RedirectView {
        return if (redirectUri != null) errorRedirect(redirectUri, error, state)
        else RedirectView("?error=$error")
    }
}
