package org.iamsso.services.userservice.controller

import jakarta.validation.Valid
import org.iamsso.contracts.user.model.UpdateProfileRequest
import org.iamsso.services.userservice.service.UserProfileService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/{userId}/profile")
class UserProfileController(private val userProfileService: UserProfileService) {

    @GetMapping
    fun get(@PathVariable userId: UUID) = userProfileService.get(userId)

    @PatchMapping
    fun update(@PathVariable userId: UUID, @Valid @RequestBody req: UpdateProfileRequest) =
        userProfileService.update(userId, req)
}