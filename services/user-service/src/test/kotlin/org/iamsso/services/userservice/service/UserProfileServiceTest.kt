package org.iamsso.services.userservice.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.iamsso.contracts.user.model.UpdateProfileRequest
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserProfileEntity
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.repository.UserProfileRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceTest {

    @Mock lateinit var userRepo: UserRepository
    @Mock lateinit var profileRepo: UserProfileRepository
    @Mock lateinit var events: EventPublisher

    private lateinit var service: UserProfileService

    @BeforeEach
    fun setUp() {
        service = UserProfileService(userRepo, profileRepo, events)
    }

    // ── get ──────────────────────────────────────────────────────────────────

    @Test
    fun `get user not found throws UserNotFoundException`() {
        val id = UUID.randomUUID()
        whenever(userRepo.findById(id)).thenReturn(Optional.empty())

        assertThatThrownBy { service.get(id) }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun `get returns profile response even when profile is null`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h", displayName = "Alice")
        user.profile = null
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        val result = service.get(id)

        assertThat(result.userId).isEqualTo(id)
        assertThat(result.firstName).isNull()
    }

    @Test
    fun `get returns full profile when profile exists`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h")
        val profile = UserProfileEntity(user = user, firstName = "Alice", timezone = "UTC")
        user.profile = profile
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        val result = service.get(id)

        assertThat(result.firstName).isEqualTo("Alice")
        assertThat(result.timezone).isEqualTo("UTC")
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    fun `update creates profile if missing and publishes event`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h")
        user.profile = null
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        service.update(id, UpdateProfileRequest(firstName = "Bob"))

        verify(profileRepo).save(any())
        verify(events).userProfileUpdated(any(), any())
    }

    @Test
    fun `update updates fields on existing profile`() {
        val id = UUID.randomUUID()
        val user = UserEntity(id = id, passwordHash = "h")
        val profile = UserProfileEntity(user = user, firstName = "Old")
        user.profile = profile
        whenever(userRepo.findById(id)).thenReturn(Optional.of(user))

        service.update(id, UpdateProfileRequest(firstName = "New", timezone = "Europe/Moscow"))

        assertThat(profile.firstName).isEqualTo("New")
        assertThat(profile.timezone).isEqualTo("Europe/Moscow")
        verify(events).userProfileUpdated(any(), any())
    }
}
