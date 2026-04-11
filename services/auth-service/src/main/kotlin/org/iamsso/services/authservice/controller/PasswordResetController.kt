package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.service.UserServiceClient
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.view.RedirectView

@Controller
class PasswordResetController(
    private val userServiceClient: UserServiceClient,
) {

    // ── /forgot-password ─────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    fun showForgotPasswordForm(
        @RequestParam("sent", required = false) sent: String?,
        model: Model,
    ): String {
        model.addAttribute("sent", sent != null)
        return "forgot-password"
    }

    @PostMapping("/forgot-password")
    fun submitForgotPasswordForm(
        @RequestParam("email") email: String,
    ): RedirectView {
        userServiceClient.requestPasswordReset(email)
        return RedirectView("/forgot-password?sent=1")
    }

    // ── /reset-password ──────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    fun showResetPasswordForm(
        @RequestParam("token") token: String,
        model: Model,
    ): String {
        val valid = userServiceClient.validatePasswordResetToken(token)
        model.addAttribute("token", token)
        if (!valid) model.addAttribute("error", "token_invalid")
        return "reset-password"
    }

    @PostMapping("/reset-password")
    fun submitResetPasswordForm(
        @RequestParam("token") token: String,
        @RequestParam("newPassword") newPassword: String,
        @RequestParam("confirmPassword") confirmPassword: String,
        model: Model,
    ): Any {
        if (newPassword != confirmPassword) {
            model.addAttribute("token", token)
            model.addAttribute("error", "mismatch")
            return "reset-password"
        }

        val errorCode = userServiceClient.confirmPasswordReset(token, newPassword)
        return when (errorCode) {
            null -> RedirectView("/login?message=password_reset_success")
            "INVALID_PASSWORD" -> {
                model.addAttribute("token", token)
                model.addAttribute("error", "weak_password")
                "reset-password"
            }
            "TOKEN_INVALID" -> {
                model.addAttribute("error", "token_invalid")
                "reset-password"
            }
            else -> {
                model.addAttribute("token", token)
                model.addAttribute("error", "weak_password")
                "reset-password"
            }
        }
    }
}
