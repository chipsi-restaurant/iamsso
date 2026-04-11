package org.iamsso.services.userservice.controller

import jakarta.validation.Valid
import org.iamsso.contracts.user.model.PasswordResetConfirmRequest
import org.iamsso.contracts.user.model.PasswordResetRequestRequest
import org.iamsso.services.userservice.service.PasswordResetService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/password-reset")
class PasswordResetController(private val service: PasswordResetService) {

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun request(@Valid @RequestBody req: PasswordResetRequestRequest) {
        service.requestReset(req.email)
    }

    @GetMapping("/validate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun validate(@RequestParam token: String) {
        service.validate(token)
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirm(@Valid @RequestBody req: PasswordResetConfirmRequest) {
        service.confirmReset(req.token, req.newPassword)
    }
}
