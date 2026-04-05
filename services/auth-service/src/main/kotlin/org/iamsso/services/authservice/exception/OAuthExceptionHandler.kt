package org.iamsso.services.authservice.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class OAuthExceptionHandler {

    @ExceptionHandler(OAuthException::class)
    fun handle(ex: OAuthException): ResponseEntity<Map<String, String>> {
        val body = mutableMapOf("error" to ex.error, "error_description" to ex.errorDescription)
        return ResponseEntity.status(ex.httpStatus).body(body)
    }
}