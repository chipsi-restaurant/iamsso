package org.iamsso.services.userservice.service

import org.iamsso.contracts.user.model.UpdateProfileRequest
import org.iamsso.contracts.user.model.UserProfileResponse
import org.iamsso.services.userservice.entity.UserProfileEntity
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.mapper.UserMapper
import org.iamsso.services.userservice.repository.UserProfileRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserProfileService(
    private val userRepo: UserRepository,
    private val profileRepo: UserProfileRepository,
    private val events: EventPublisher,
) {

    @Transactional(readOnly = true)
    fun get(userId: UUID): UserProfileResponse {
        val user = userRepo.findById(userId).orElseThrow { UserNotFoundException(userId) }
        return UserMapper.toProfileResponse(user, user.profile)
    }

    @Transactional
    fun update(userId: UUID, request: UpdateProfileRequest): UserProfileResponse {
        val user = userRepo.findById(userId).orElseThrow { UserNotFoundException(userId) }
        val profile = user.profile ?: UserProfileEntity(user = user).also { user.profile = it }
        val changed = mutableListOf<String>()

        request.displayName?.let { user.displayName = it; changed += "displayName" }
        request.firstName?.let { profile.firstName = it; changed += "firstName" }
        request.lastName?.let { profile.lastName = it; changed += "lastName" }
        request.avatarUrl?.let { profile.avatarUrl = it.toString(); changed += "avatarUrl" }
        request.timezone?.let { profile.timezone = it; changed += "timezone" }
        request.locale?.let { user.locale = it; changed += "locale" }

        profileRepo.save(profile)
        if (changed.isNotEmpty()) events.userProfileUpdated(userId, changed)
        return UserMapper.toProfileResponse(user, profile)
    }
}