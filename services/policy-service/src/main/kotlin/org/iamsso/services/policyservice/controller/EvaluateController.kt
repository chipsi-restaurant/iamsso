package org.iamsso.services.policyservice.controller

import jakarta.validation.Valid
import org.iamsso.services.policyservice.mapper.EvaluateRequest
import org.iamsso.services.policyservice.service.PolicyEvaluator
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/policy")
class EvaluateController(private val evaluator: PolicyEvaluator) {

    @PostMapping("/evaluate")
    fun evaluate(@Valid @RequestBody request: EvaluateRequest) = evaluator.evaluate(request)
}
