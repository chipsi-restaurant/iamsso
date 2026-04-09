package org.iamsso.services.mfaservice.exception

open class ServiceException(val code: String, override val message: String) : RuntimeException(message)

class MfaFactorNotFoundException(id: Any) : ServiceException("MFA_FACTOR_NOT_FOUND", "MFA-фактор не найден: $id")
class MfaFactorAlreadyExistsException(type: String) : ServiceException("MFA_FACTOR_EXISTS", "Фактор типа $type уже зарегистрирован")
class InvalidMfaCodeException : ServiceException("INVALID_MFA_CODE", "Неверный код подтверждения")
class NoActiveFactorException : ServiceException("NO_ACTIVE_FACTOR", "Нет активных MFA-факторов для верификации")
