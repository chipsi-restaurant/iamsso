package org.iamsso.services.policyservice.exception

import org.iamsso.contracts.user.model.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(RoleNotFoundException::class, PolicyNotFoundException::class)
    fun handleNotFound(ex: ServiceException): ResponseEntity<ErrorResponse> =
        response(HttpStatus.NOT_FOUND, ex)

    @ExceptionHandler(RoleAlreadyExistsException::class, PolicyAlreadyExistsException::class)
    fun handleConflict(ex: ServiceException): ResponseEntity<ErrorResponse> =
        response(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(RoleInUseException::class)
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
