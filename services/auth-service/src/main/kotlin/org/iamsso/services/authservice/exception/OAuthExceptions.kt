package org.iamsso.services.authservice.exception

open class OAuthException(
    val error: String,
    val errorDescription: String,
    val httpStatus: Int = 400,
) : RuntimeException(errorDescription)

class InvalidRequestException(desc: String) : OAuthException("invalid_request", desc)
class InvalidClientException(desc: String) : OAuthException("invalid_client", desc, 401)
class InvalidGrantException(desc: String) : OAuthException("invalid_grant", desc)
class UnauthorizedClientException(desc: String) : OAuthException("unauthorized_client", desc)
class UnsupportedGrantTypeException(desc: String) : OAuthException("unsupported_grant_type", desc)
class InvalidScopeException(desc: String) : OAuthException("invalid_scope", desc)
class AuthorizationPendingException : OAuthException("authorization_pending", "Пользователь ещё не подтвердил авторизацию")
class SlowDownException : OAuthException("slow_down", "Слишком частые запросы")
class ExpiredTokenException(desc: String) : OAuthException("expired_token", desc)
class AccessDeniedException(desc: String) : OAuthException("access_denied", desc)
class ClientNotFoundException(id: String) : OAuthException("invalid_client", "Клиент не найден: $id", 404)