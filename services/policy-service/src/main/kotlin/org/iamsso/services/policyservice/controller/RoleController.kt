package org.iamsso.services.policyservice.controller

import jakarta.validation.Valid
import org.iamsso.services.policyservice.mapper.CreateRoleRequest
import org.iamsso.services.policyservice.service.RoleService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/roles")
class RoleController(private val roleService: RoleService) {

    @GetMapping
    fun list() = roleService.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateRoleRequest) = roleService.create(request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = roleService.delete(id)
}
