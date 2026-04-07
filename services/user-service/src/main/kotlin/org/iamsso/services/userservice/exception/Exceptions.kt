package org.iamsso.services.userservice.exception

open class ServiceException(val code: String, override val message: String) : RuntimeException(message)

class UserNotFoundException(id: Any) : ServiceException("USER_NOT_FOUND", "Пользователь не найден: $id")
class UserAlreadyExistsException(field: String, value: String) : ServiceException("USER_ALREADY_EXISTS", "Пользователь с $field '$value' уже существует")
class InvalidCredentialsException : ServiceException("INVALID_CURRENT_PASSWORD", "Текущий пароль указан неверно")
class AccountLockedException(until: String) : ServiceException("ACCOUNT_LOCKED", "Аккаунт заблокирован до $until")
class InvalidVerificationTokenException : ServiceException("INVALID_VERIFICATION_TOKEN", "Токен подтверждения недействителен или истёк")
class MfaFactorAlreadyExistsException(type: String) : ServiceException("MFA_FACTOR_EXISTS", "Фактор типа $type уже зарегистрирован")
class MfaFactorNotFoundException(id: Any) : ServiceException("MFA_FACTOR_NOT_FOUND", "MFA-фактор не найден: $id")
class InvalidMfaCodeException : ServiceException("INVALID_MFA_CODE", "Неверный код подтверждения")
class RateLimitExceededException(seconds: Long) : ServiceException("RATE_LIMIT_EXCEEDED", "Повторная отправка возможна через $seconds секунд")