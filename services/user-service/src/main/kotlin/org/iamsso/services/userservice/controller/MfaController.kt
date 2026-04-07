package org.iamsso.services.userservice.controller

import jakarta.validation.Valid
import org.iamsso.contracts.user.model.EnrollMfaRequest
import org.iamsso.contracts.user.model.VerifyMfaCodeRequest
import org.iamsso.services.userservice.service.MfaService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/{userId}/mfa")
class MfaController(private val mfaService: MfaService) {

    @GetMapping("/factors")
    fun list(@PathVariable userId: UUID) = mfaService.listFactors(userId)

    @PostMapping("/factors")
    @ResponseStatus(HttpStatus.CREATED)
    fun enroll(@PathVariable userId: UUID, @Valid @RequestBody req: EnrollMfaRequest) = mfaService.enroll(userId, req)

    @PostMapping("/factors/{factorId}/verify")
    fun confirm(
        @PathVariable userId: UUID,
        @PathVariable factorId: UUID,
        @Valid @RequestBody req: VerifyMfaCodeRequest
    ) =
        mfaService.confirm(userId, factorId, req.code)

    @DeleteMapping("/factors/{factorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable userId: UUID, @PathVariable factorId: UUID) = mfaService.remove(userId, factorId)

    @GetMapping("/status")
    fun status(@PathVariable userId: UUID) = mfaService.getStatus(userId)
}