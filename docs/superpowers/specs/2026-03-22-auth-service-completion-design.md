# Auth Service — Завершение (дизайн)

**Дата:** 2026-03-22
**Статус:** Approved
**Область:** `services/auth-service`, `libs/contracts`

---

## Контекст

Auth-service реализует OAuth 2.0 / OpenID Connect Authorization Server. На момент написания ~80% готово:
entity, repository, TokenService, ClientService, grant handlers, TokenController, RevocationController,
IntrospectionController, DeviceController, JwksController (заглушка), DiscoveryController (заглушка).

Остаётся написать: AuthorizationController, UserInfoController, ClientController, SessionController,
AuthEventPublisher, UserKafkaConsumer, заполнить JwksController/DiscoveryController.

---

## Архитектурные решения

### SecurityConfig — два FilterChain

```
Chain 1 (@Order(1)) — OAuth/OIDC
  matcher: /oauth2/**, /.well-known/**, /userinfo, /actuator/**
  session: STATELESS
  csrf: disabled
  auth: permitAll (userinfo проверяет JWT вручную в контроллере)

Chain 2 (@Order(2)) — Admin API
  matcher: /api/v1/**
  session: STATELESS
  csrf: disabled
  auth: oauth2ResourceServer { jwt { decoder = localJwtDecoder() } }
       + hasAuthority("SCOPE_iam:admin")
```

`localJwtDecoder()` — `NimbusJwtDecoder` с RSA публичным ключом из `JwtKeyProvider`. Без внешних вызовов.

### SSO-сессия — интерфейс + Redis-реализация

```kotlin
interface SsoSessionService {
    fun create(userId: UUID, clientId: String): SsoSession
    fun get(sessionId: String): SsoSession?
    fun addClient(sessionId: String, clientId: String)
    fun delete(sessionId: String)
    fun deleteAllForUser(userId: UUID)
}

class RedisSsoSessionService(...) : SsoSessionService  // текущая реализация
// class RemoteSsoSessionService(...) : SsoSessionService  // будущий session-service
```

Сессия хранится в Redis: `sso:session:{sessionId}` с TTL. Передаётся через HttpOnly cookie `SSO_SESSION`.
Интерфейс позволяет вынести сессии в отдельный микросервис без изменений в контроллерах.

---

## Новые компоненты

### Сервисы

| Класс | Ответственность |
|---|---|
| `AuthRequestService` | Хранит параметры pending `/oauth2/authorize` в Redis (`auth:request:{uuid}`, TTL 5 мин) |
| `RedisSsoSessionService` | CRUD SSO-сессий в Redis, создаёт/удаляет cookie `SSO_SESSION` |
| `AuthEventPublisher` | Публикует Kafka-события в `iam.auth.events` и `iam.session.events` |
| `UserKafkaConsumer` | Слушает `iam.user.events`, отзывает токены при `user.deleted` / `user.status-changed` |

### Контроллеры

| Класс | Эндпоинты |
|---|---|
| `AuthorizationController` | `GET /oauth2/authorize`, `POST /oauth2/authorize/callback` |
| `UserInfoController` | `GET /userinfo` |
| `ClientController` | `POST/GET /api/v1/clients`, `GET/PATCH/DELETE /api/v1/clients/{id}`, `POST /api/v1/clients/{id}/secret/rotate` |
| `SessionController` | `GET /api/v1/sessions/current`, `POST /api/v1/sessions/logout`, `POST /api/v1/sessions/logout-all` |

### Data-классы (Redis, не JPA)

```kotlin
data class SsoSession(
    val sessionId: String,
    val userId: UUID,
    val clientIds: MutableList<String>,
    val createdAt: Instant,
    val expiresAt: Instant,
)

data class AuthRequest(
    val authRequestId: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String?,
    val state: String?,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val nonce: String?,
)
```

### Contracts (libs/contracts)

- `AuthEvents.kt` — `TokenIssuedEvent`, `TokenRevokedEvent`, `LoginSuccessEvent`, `LoginFailedEvent`
- `SessionEvents.kt` — `SessionCreatedEvent`, `SessionDestroyedEvent`
- `KafkaTopics.kt` — добавить `AUTH_EVENTS = "iam.auth.events"`, `SESSION_EVENTS = "iam.session.events"`

### Конфиг (AppProperties)

```kotlin
data class LoginPageProps(val url: String = "http://localhost:3000")
data class AdminProps(val scope: String = "iam:admin")
// добавить в AppProperties: val loginPage, val admin
```

---

## Authorization Flow

### GET /oauth2/authorize

1. Валидация: `client_id` существует, `redirect_uri` зарегистрирован, `response_type=code`, `code_challenge` присутствует, `code_challenge_method=S256`
   - Ошибки по `client_id` / `redirect_uri` → `400 OAuthError` **без редиректа** (RFC 6749)
   - Остальные ошибки → редирект с `error=` параметром
2. Проверить cookie `SSO_SESSION` → `SsoSessionService.get()` → если валидна → silent SSO (пункт 5)
3. Сохранить `AuthRequest` в Redis через `AuthRequestService.save()` (TTL 5 мин)
4. Редирект: `{app.loginPage.url}/login?auth_request_id={uuid}`
5. (Silent SSO) Создать auth code → `302` на `redirect_uri?code=...&state=...`

### POST /oauth2/authorize/callback

Тело `application/x-www-form-urlencoded`: `auth_request_id`, `username`, `password`

1. Загрузить `AuthRequest` из Redis → `400` если не найден или истёк
2. `UserServiceClient.getByEmail()` или `getByUsername()` — найти пользователя
3. `UserServiceClient.verifyCredentials()` — проверить пароль
   - Неверно → `LoginFailedEvent` → `302` на `{loginPage}?auth_request_id={id}&error=invalid_credentials`
4. Проверить `user.status == ACTIVE` → иначе `error=account_disabled`
5. `SsoSessionService.create(userId, clientId)` → установить HttpOnly cookie `SSO_SESSION`
6. `AuthorizationCodeStore.save(code, codeData)` — создать auth code
7. `AuthRequestService.delete(authRequestId)` — удалить из Redis
8. `AuthEventPublisher.publish(LoginSuccessEvent(...))` + `SessionCreatedEvent`
9. `302` → `redirect_uri?code={code}&state={state}`

---

## UserInfoController

`GET /userinfo` — Bearer JWT в заголовке `Authorization`

1. Распарсить и верифицировать JWT через `JwtKeyProvider` (RSA публичный ключ, локально)
2. Извлечь `sub` (userId) и `scope` из claims
3. Вызвать `UserServiceClient` по необходимости:
   - `scope` содержит `email` или `profile` → `getByEmail()` / `getProfile()`
4. Собрать `UserInfoResponse` по scope:
   - `openid` → `sub`
   - `+ email` → `email`, `email_verified`
   - `+ profile` → `preferred_username`, `name`, `given_name`, `family_name`, `locale`, `picture`

---

## ClientController

Тонкий слой над существующим `ClientService`.

- Защита через Chain 2: Bearer JWT с `SCOPE_iam:admin`
- `POST /api/v1/clients` → `ClientService.register()` → `201 ClientWithSecretResponse`
- `GET /api/v1/clients` → `ClientService.findAll()` → `200 List<ClientResponse>`
- `GET /api/v1/clients/{id}` → `ClientService.findById()` → `200` / `404`
- `PATCH /api/v1/clients/{id}` → `ClientService.update()` → `200 ClientResponse`
- `DELETE /api/v1/clients/{id}` → `ClientService.delete()` + отзыв всех токенов → `204`
- `POST /api/v1/clients/{id}/secret/rotate` → `ClientService.rotateSecret()` → `200 ClientWithSecretResponse`

---

## SessionController

Читает userId из cookie `SSO_SESSION` через `SsoSessionService`, не из JWT.

- `GET /api/v1/sessions/current` → `SsoSessionService.get(sessionId)` → `200 SessionResponse` / `401`
- `POST /api/v1/sessions/logout`:
  1. `SsoSessionService.delete(sessionId)`
  2. `RefreshTokenRepository.revokeAllForUserAndClient(userId, clientId)`
  3. `AuthEventPublisher.publish(SessionDestroyedEvent(reason=logout))`
  4. `Set-Cookie: SSO_SESSION=; Max-Age=0`
  5. Редирект на `post_logout_redirect_uri` если передан, иначе `200`
- `POST /api/v1/sessions/logout-all`:
  1. `SsoSessionService.deleteAllForUser(userId)`
  2. `RefreshTokenRepository.revokeAllForUser(userId)`
  3. `AuthEventPublisher.publish(SessionDestroyedEvent(reason=logout-all))`
  4. `Set-Cookie: SSO_SESSION=; Max-Age=0` → `200`

---

## AuthEventPublisher

Публикует через `KafkaTemplate<String, CloudEventEnvelope>` (ключ = userId или clientId).

| Событие | Топик | Публикуется в |
|---|---|---|
| `LoginSuccessEvent` | `iam.auth.events` | `AuthorizationController.callback` — успех |
| `LoginFailedEvent` | `iam.auth.events` | `AuthorizationController.callback` — неверный пароль |
| `TokenIssuedEvent` | `iam.auth.events` | `TokenController` — успешная выдача |
| `TokenRevokedEvent` | `iam.auth.events` | `RevocationController` |
| `SessionCreatedEvent` | `iam.session.events` | `RedisSsoSessionService.create()` |
| `SessionDestroyedEvent` | `iam.session.events` | logout / logout-all |

---

## UserKafkaConsumer

```kotlin
@KafkaListener(topics = [KafkaTopics.USER_EVENTS], groupId = "auth-service")
fun onUserEvent(envelope: CloudEventEnvelope) {
    when (val event = envelope.data) {
        is UserDeletedEvent ->
            refreshTokenRepository.revokeAllForUser(event.userId)
            ssoSessionService.deleteAllForUser(event.userId)
        is UserStatusChangedEvent ->
            if (event.newStatus != "ACTIVE") {
                refreshTokenRepository.revokeAllForUser(event.userId)
                ssoSessionService.deleteAllForUser(event.userId)
            }
        else -> Unit
    }
}
```

---

## Что не входит в этот этап

- ГОСТ-ключи в JwksController (заглушка остаётся)
- MFA в authorization flow (отдельный mfa-service)
- Consent flow (отдельный consent-service)
- JWT-авторизация в user-service (следующий этап)
- docker-compose (следующий этап)
