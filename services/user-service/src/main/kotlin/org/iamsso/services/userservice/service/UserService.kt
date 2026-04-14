package org.iamsso.services.userservice.service

import org.iamsso.contracts.user.model.ChangeStatusRequest
import org.iamsso.contracts.user.model.CreateUserRequest
import org.iamsso.contracts.user.model.UpdateUserRequest
import org.iamsso.contracts.user.model.UserResponse
import org.iamsso.contracts.user.model.UserStatus
import org.iamsso.services.userservice.config.AppProperties
import org.iamsso.services.userservice.entity.EmailVerificationTokenEntity
import org.iamsso.services.userservice.entity.UserEntity
import org.iamsso.services.userservice.entity.UserStatus as EntityUserStatus
import org.iamsso.services.userservice.entity.UserProfileEntity
import org.iamsso.services.userservice.exception.InvalidVerificationTokenException
import org.iamsso.services.userservice.exception.RateLimitExceededException
import org.iamsso.services.userservice.exception.ServiceException
import org.iamsso.services.userservice.exception.UserAlreadyExistsException
import org.iamsso.services.userservice.exception.UserNotFoundException
import org.iamsso.services.userservice.mapper.UserMapper
import org.iamsso.services.userservice.mapper.UserMapper.toEntity
import org.iamsso.services.userservice.mapper.UserMapper.toEntityOrNull
import org.iamsso.services.userservice.repository.EmailVerificationTokenRepository
import org.iamsso.services.userservice.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class UserService(
    private val userRepo: UserRepository,
    private val tokenRepo: EmailVerificationTokenRepository,
    private val encoder: PasswordEncoder,
    private val events: EventPublisher,
    private val props: AppProperties,
) {

    @Transactional
    fun create(request: CreateUserRequest): UserResponse {
        request.email?.let { if (userRepo.existsByEmail(it)) throw UserAlreadyExistsException("email", it) }
        request.username?.let { if (userRepo.existsByUsername(it)) throw UserAlreadyExistsException("username", it) }

        val user = UserEntity(
            email = request.email,
            username = request.username,
            displayName = request.displayName,
            passwordHash = encoder.encode(request.password) ?: throw ServiceException("PASSWORD_HASHING_FAILED", "Failed to hash password"),
            locale = request.locale ?: "en",
        )
        request.role?.let { user.role = it.value }
        if (request.activate == true) {
            user.status = EntityUserStatus.ACTIVE
            user.emailVerified = true
        }
        user.profile = UserProfileEntity(user = user)
        userRepo.save(user)

        if (user.email != null && request.activate != true) createAndSendVerificationToken(user)
        events.userCreated(user.id, user.email, user.username, user.status.name)
        return UserMapper.toResponse(user)
    }

    @Transactional(readOnly = true)
    fun getById(userId: UUID) = UserMapper.toResponse(findUser(userId))

    @Transactional(readOnly = true)
    fun getByEmail(email: String) =
        UserMapper.toResponse(userRepo.findByEmail(email) ?: throw UserNotFoundException(email))

    @Transactional(readOnly = true)
    fun getByUsername(username: String) =
        UserMapper.toResponse(userRepo.findByUsername(username) ?: throw UserNotFoundException(username))

    @Transactional(readOnly = true)
    fun list(status: UserStatus?, query: String?, page: Int, size: Int) =
        UserMapper.toPagedResponse(userRepo.search(status.toEntityOrNull(), query, PageRequest.of(page, size)))

    @Transactional
    fun update(userId: UUID, request: UpdateUserRequest): UserResponse {
        val user = findUser(userId)
        val changed = mutableListOf<String>()

        request.email?.let {
            if (it != user.email && userRepo.existsByEmail(it)) throw UserAlreadyExistsException("email", it)
            user.email = it; user.emailVerified = false; changed += "email"
        }
        request.username?.let {
            if (it != user.username && userRepo.existsByUsername(it)) throw UserAlreadyExistsException("username", it)
            user.username = it; changed += "username"
        }
        request.displayName?.let { user.displayName = it; changed += "displayName" }
        request.locale?.let { user.locale = it; changed += "locale" }

        if (changed.isNotEmpty()) events.userUpdated(user.id, changed)
        return UserMapper.toResponse(user)
    }

    @Transactional
    fun delete(userId: UUID) {
        findUser(userId).also { it.status = EntityUserStatus.DELETED; events.userDeleted(it.id) }
    }

    @Transactional
    fun changeStatus(userId: UUID, request: ChangeStatusRequest): UserResponse {
        val user = findUser(userId)
        val old = user.status.name
        user.status = request.status.toEntity()
        events.userStatusChanged(user.id, old, user.status.name, request.reason)
        return UserMapper.toResponse(user)
    }

    @Transactional
    fun verifyEmail(userId: UUID, token: String): UserResponse {
        val entity = tokenRepo.findByToken(token) ?: throw InvalidVerificationTokenException()
        if (entity.userId != userId || entity.expiresAt.isBefore(Instant.now())) throw InvalidVerificationTokenException()

        val user = findUser(userId)
        user.emailVerified = true
        if (user.status == EntityUserStatus.PENDING_VERIFICATION) user.status = EntityUserStatus.ACTIVE
        tokenRepo.deleteAllByUserId(userId)
        return UserMapper.toResponse(user)
    }

    @Transactional
    fun resendEmailVerification(userId: UUID) {
        val user = findUser(userId)
        val email = user.email ?: throw ServiceException("NO_EMAIL", "У пользователя не указан email")

        tokenRepo.findFirstByUserIdOrderByCreatedAtDesc(userId)?.let {
            val elapsed = Duration.between(it.createdAt, Instant.now()).seconds
            val cooldown = props.emailVerification.resendCooldownSeconds
            if (elapsed < cooldown) throw RateLimitExceededException(cooldown - elapsed)
        }

        tokenRepo.deleteAllByUserId(userId)
        createAndSendVerificationToken(user)
    }

    private fun createAndSendVerificationToken(user: UserEntity) {
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(props.emailVerification.tokenTtlHours, ChronoUnit.HOURS)
        tokenRepo.save(EmailVerificationTokenEntity(userId = user.id, token = token, expiresAt = expiresAt))
        events.sendEmailVerification(user.id, user.email!!, token, expiresAt)
    }

    private fun findUser(userId: UUID) =
        userRepo.findById(userId).orElseThrow { UserNotFoundException(userId) }
}