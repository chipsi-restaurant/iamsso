package org.iamsso.services.userservice.controller

import org.iamsso.services.userservice.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/{userId}/verify-email")
class EmailVerificationController(private val userService: UserService) {

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun verifyEmail(
        @PathVariable userId: UUID,
        @RequestParam token: String,
    ): ResponseEntity<String> {
        return try {
            userService.verifyEmail(userId, token)
            ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(successPage())
        } catch (e: Exception) {
            ResponseEntity.badRequest().contentType(MediaType.TEXT_HTML)
                .body(errorPage(e.message ?: "Не удалось подтвердить email"))
        }
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resend(@PathVariable userId: UUID) = userService.resendEmailVerification(userId)

    private fun successPage(): String = """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8">
            <title>Email подтверждён</title>
            <style>
                body { font-family: system-ui, -apple-system, sans-serif; background: #f5f5f7; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
                .card { background: white; padding: 40px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); max-width: 400px; text-align: center; }
                h1 { color: #1a1a2e; font-size: 20px; margin: 0 0 12px; font-weight: 500; }
                p { color: #6b7280; font-size: 14px; margin: 0 0 24px; }
                a { display: inline-block; background: #7c3aed; color: white; text-decoration: none; padding: 10px 20px; border-radius: 6px; font-size: 14px; font-weight: 500; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>Email подтверждён</h1>
                <p>Ваш email успешно подтверждён. Теперь вы можете войти в систему.</p>
                <a href="/login">Войти</a>
            </div>
        </body>
        </html>
    """.trimIndent()

    private fun errorPage(message: String): String = """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8">
            <title>Ошибка подтверждения</title>
            <style>
                body { font-family: system-ui, -apple-system, sans-serif; background: #f5f5f7; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
                .card { background: white; padding: 40px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); max-width: 400px; text-align: center; }
                h1 { color: #dc2626; font-size: 20px; margin: 0 0 12px; font-weight: 500; }
                p { color: #6b7280; font-size: 14px; margin: 0; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>Ошибка подтверждения</h1>
                <p>${message.replace("<", "&lt;").replace(">", "&gt;")}</p>
            </div>
        </body>
        </html>
    """.trimIndent()
}
