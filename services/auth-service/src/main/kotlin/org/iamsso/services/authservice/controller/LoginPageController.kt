package org.iamsso.services.authservice.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class LoginPageController {

    @GetMapping("/login")
    fun loginPage(
        @RequestParam("auth_request_id") authRequestId: String,
        @RequestParam("error", required = false) error: String?,
        model: Model,
    ): String {
        model.addAttribute("authRequestId", authRequestId)
        model.addAttribute("error", error)
        return "login"
    }
}
