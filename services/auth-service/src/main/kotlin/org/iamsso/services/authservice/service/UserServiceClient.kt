package org.iamsso.services.authservice.service

import org.iamsso.contracts.user.api.InternalApi
import org.iamsso.contracts.user.api.ProfileApi
import org.iamsso.contracts.user.api.UsersApi
import org.iamsso.contracts.user.model.VerifyCredentialsRequest
import org.iamsso.services.authservice.config.AppProperties
import org.springframework.core.task.VirtualThreadTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

data class UserData(
    val id: UUID,
    val email: String?,
    val username: String?,
    val displayName: String?,
    val status: String,
    val emailVerified: Boolean,
    val mfaEnabled: Boolean,
    val locale: String?,
    val role: String?,
)

data class CredentialsResult(
    val valid: Boolean,
    val userId: UUID,
    val status: String,
    val mfaRequired: Boolean,
    val failedAttempts: Int,
    val lockedUntil: String?,
)

data class UserProfile(
    val userId: UUID,
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val avatarUrl: String?,
    val timezone: String?,
    val locale: String?,
)

@Component
class UserServiceClient(
    private val appProperties: AppProperties
) {

    private val internalApi = InternalApi(appProperties.userService.baseUrl)
    private val profileApi = ProfileApi(appProperties.userService.baseUrl)
    private val usersApi = UsersApi(appProperties.userService.baseUrl)

    fun getByEmail(email: String): UserData? =
        runCatchingRest { internalApi.getUserByEmail(email).toUserData() }

    fun getByUsername(username: String): UserData? =
        runCatchingRest { internalApi.getUserByUsername(username).toUserData() }

    fun verifyCredentials(userId: UUID, password: String): CredentialsResult? =
        runCatchingRest {
            internalApi.verifyCredentials(
                userId = userId,
                verifyCredentialsRequest = VerifyCredentialsRequest(password = password)
            ).toCredentialsResult()
        }

    fun getById(userId: UUID): UserData? =
        runCatchingRest { usersApi.getUserById(userId).toUserData() }

    fun getProfile(userId: UUID): UserProfile? =
        runCatchingRest { profileApi.getUserProfile(userId).toUserProfile() }

    private fun <T> runCatchingRest(block: () -> T): T? =
        try {
            block()
        } catch (_: RestClientResponseException) {
            null
        }

    private fun org.iamsso.contracts.user.model.UserResponse.toUserData(): UserData =
        UserData(
            id = id!!,
            email = email,
            username = username,
            displayName = displayName,
            status = status!!.toString(),
            emailVerified = emailVerified!!,
            mfaEnabled = mfaEnabled!!,
            locale = locale,
            role = role
        )

    private fun org.iamsso.contracts.user.model.CredentialsVerificationResponse.toCredentialsResult(): CredentialsResult =
        CredentialsResult(
            valid = valid!!,
            userId = userId!!,
            status = status!!.toString(),
            mfaRequired = mfaRequired!!,
            failedAttempts = failedAttempts!!,
            lockedUntil = lockedUntil?.toString()
        )

    private fun org.iamsso.contracts.user.model.UserProfileResponse.toUserProfile(): UserProfile =
        UserProfile(
            userId = userId!!,
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            avatarUrl = avatarUrl,
            timezone = timezone,
            locale = locale
        )
}