package org.iamsso.services.mfaservice.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MfaFactorNotFoundException::class)
    fun handleNotFound(ex: ServiceException): ResponseEntity<ErrorResponse> =
        response(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(MfaFactorAlreadyExistsException::class)
    fun handleConflict(ex: ServiceException): ResponseEntity<ErrorResponse> =
        response(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(InvalidMfaCodeException::class, NoActiveFactorException::class)
    fun handleBadRequest(ex: ServiceException): ResponseEntity<ErrorResponse> =
        response(HttpStatus.BAD_REQUEST, ex)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "") }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(code = "VALIDATION_ERROR", message = "Ошибка валидации", details = details))
    }

    private fun response(status: HttpStatus, ex: ServiceException) =
        ResponseEntity.status(status).body(ErrorResponse(code = ex.code, message = ex.message))
}
