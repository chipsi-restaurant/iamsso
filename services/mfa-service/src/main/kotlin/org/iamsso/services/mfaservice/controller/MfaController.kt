package org.iamsso.services.mfaservice.controller

import jakarta.validation.Valid
import org.iamsso.contracts.mfa.model.ConfirmMfaRequest
import org.iamsso.contracts.mfa.model.EnrollMfaFactorRequest
import org.iamsso.contracts.mfa.model.VerifyMfaRequest
import org.iamsso.services.mfaservice.service.MfaService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/mfa/{userId}")
class MfaController(private val mfaService: MfaService) {

    @GetMapping("/factors")
    fun listFactors(@PathVariable userId: UUID) = mfaService.listFactors(userId)

    @PostMapping("/factors")
    @ResponseStatus(HttpStatus.CREATED)
    fun enroll(
        @PathVariable userId: UUID,
        @Valid @RequestBody req: EnrollMfaFactorRequest,
        @RequestHeader("X-User-Email", required = false) email: String?,
    ) = mfaService.enroll(userId, req, email)

    @PostMapping("/factors/{factorId}/confirm")
    fun confirm(
        @PathVariable userId: UUID,
        @PathVariable factorId: UUID,
        @Valid @RequestBody req: ConfirmMfaRequest,
    ) = mfaService.confirm(userId, factorId, req.code)

    @DeleteMapping("/factors/{factorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable userId: UUID, @PathVariable factorId: UUID) =
        mfaService.remove(userId, factorId)

    @PostMapping("/verify")
    fun verify(
        @PathVariable userId: UUID,
        @Valid @RequestBody req: VerifyMfaRequest,
    ) = mfaService.verify(userId, req)

    @GetMapping("/status")
    fun status(@PathVariable userId: UUID) = mfaService.getStatus(userId)

    @PostMapping("/send-otp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun sendOtp(
        @PathVariable userId: UUID,
        @RequestHeader("X-User-Email", required = false) email: String?,
    ) = mfaService.sendOtp(userId, email ?: throw IllegalArgumentException("Email required"))
}
