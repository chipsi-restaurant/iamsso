package org.iamsso.services.userservice.controller


import jakarta.validation.Valid
import org.iamsso.contracts.user.model.ChangeStatusRequest
import org.iamsso.contracts.user.model.CreateUserRequest
import org.iamsso.contracts.user.model.UpdateUserRequest
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody req: CreateUserRequest) = userService.create(req)

    @GetMapping
    fun list(
        @RequestParam(required = false) status: UserStatus?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = userService.list(status, q, page, size)

    @GetMapping("/{userId}")
    fun getById(@PathVariable userId: UUID) = userService.getById(userId)

    @PatchMapping("/{userId}")
    fun update(@PathVariable userId: UUID, @Valid @RequestBody req: UpdateUserRequest) = userService.update(userId, req)

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable userId: UUID) = userService.delete(userId)

    @PutMapping("/{userId}/status")
    fun changeStatus(@PathVariable userId: UUID, @Valid @RequestBody req: ChangeStatusRequest) =
        userService.changeStatus(userId, req)

    @GetMapping("/by-email/{email}")
    fun getByEmail(@PathVariable email: String) = userService.getByEmail(email)

    @GetMapping("/by-username/{username}")
    fun getByUsername(@PathVariable username: String) = userService.getByUsername(username)
}