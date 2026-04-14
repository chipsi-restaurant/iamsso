package org.iamsso.services.authservice.controller

import jakarta.servlet.http.HttpServletRequest
import org.iamsso.services.authservice.service.TokenResponse
import org.iamsso.services.authservice.service.TokenService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TokenController(private val tokenService: TokenService) {

    @PostMapping("/oauth2/token")
    fun token(
        @RequestParam params: Map<String, String>,
        request: HttpServletRequest,
    ): TokenResponse = tokenService.issue(params, request)
}
