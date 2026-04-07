package org.iamsso.services.policyservice.controller

import jakarta.validation.Valid
import org.iamsso.services.policyservice.mapper.CreatePolicyRequest
import org.iamsso.services.policyservice.mapper.UpdatePolicyRequest
import org.iamsso.services.policyservice.service.PolicyService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/policies")
class PolicyController(private val policyService: PolicyService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = policyService.list(page, size)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID) = policyService.getById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreatePolicyRequest) = policyService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UpdatePolicyRequest) =
        policyService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = policyService.delete(id)
}
