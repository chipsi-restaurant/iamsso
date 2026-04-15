package org.iamsso.apps.vacation.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

class NotFoundException(message: String) : RuntimeException(message) {
    constructor(entityName: String, id: UUID) : this("$entityName $id not found")
}

class InvalidStateException(message: String) : RuntimeException(message)

class ForbiddenException(message: String = "Forbidden") : RuntimeException(message)

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse("NOT_FOUND", e.message ?: "Not found"))

    @ExceptionHandler(InvalidStateException::class)
    fun invalidState(e: InvalidStateException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("INVALID_STATE", e.message ?: "Invalid state"))

    @ExceptionHandler(ForbiddenException::class)
    fun forbidden(e: ForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse("FORBIDDEN", e.message ?: "Forbidden"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException) =
        ResponseEntity.badRequest().body(ErrorResponse("BAD_REQUEST", e.message ?: "Bad request"))
}
