package org.iamsso.services.userservice.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.iamsso.contracts.user.model.ChangePasswordRequest
import org.iamsso.contracts.user.model.ResetPasswordRequest
import org.iamsso.contracts.user.model.VerifyCredentialsRequest
import org.iamsso.services.userservice.service.CredentialsService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/{userId}/credentials")
class CredentialsController(private val credentialsService: CredentialsService) {

    @PostMapping("/verify")
    fun verify(
        @PathVariable userId: UUID,
        @Valid @RequestBody req: VerifyCredentialsRequest,
        http: HttpServletRequest,
    ) = credentialsService.verify(userId, req.password, http.remoteAddr, http.getHeader("User-Agent"))

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(@PathVariable userId: UUID, @Valid @RequestBody req: ChangePasswordRequest) =
        credentialsService.changePassword(userId, req.currentPassword, req.newPassword)

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(@PathVariable userId: UUID, @Valid @RequestBody req: ResetPasswordRequest) =
        credentialsService.resetPassword(userId, req.newPassword, "password-reset-flow")
}