package org.iamsso.services.userservice.exception

open class ServiceException(val code: String, override val message: String) : RuntimeException(message)

class UserNotFoundException(id: Any) : ServiceException("USER_NOT_FOUND", "Пользователь не найден: $id")
class UserAlreadyExistsException(field: String, value: String) : ServiceException("USER_ALREADY_EXISTS", "Пользователь с $field '$value' уже существует")
class InvalidCredentialsException : ServiceException("INVALID_CURRENT_PASSWORD", "Текущий пароль указан неверно")
class AccountLockedException(until: String) : ServiceException("ACCOUNT_LOCKED", "Аккаунт заблокирован до $until")
class InvalidVerificationTokenException : ServiceException("INVALID_VERIFICATION_TOKEN", "Токен подтверждения недействителен или истёк")
class RateLimitExceededException(seconds: Long) : ServiceException("RATE_LIMIT_EXCEEDED", "Повторная отправка возможна через $seconds секунд")
class PasswordResetTokenInvalidException : ServiceException("TOKEN_INVALID", "Ссылка для сброса пароля недействительна или истекла")
class InvalidPasswordException : ServiceException("INVALID_PASSWORD", org.iamsso.services.userservice.service.PasswordPolicy.errorMessage)