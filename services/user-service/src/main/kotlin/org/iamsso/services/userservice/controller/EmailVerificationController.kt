package org.iamsso.services.userservice.controller

import org.iamsso.services.userservice.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/{userId}/verify-email")
class EmailVerificationController(private val userService: UserService) {

    @GetMapping
    fun verifyEmail(
        @PathVariable userId: UUID,
        @RequestParam token: String,
    ) = userService.verifyEmail(userId, token)

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resend(@PathVariable userId: UUID) = userService.resendEmailVerification(userId)
}
