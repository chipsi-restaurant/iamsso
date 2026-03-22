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

## Обязательные правки в существующем коде

### 0. AuthorizationCodeStore — исправить package

Файл: `repository/AuthorizationCodeStore.kt`

Текущий `package com.iam.auth.repository` — опечатка. Исправить на:
```kotlin
package org.iamsso.services.authservice.repository
```
Без этого `AuthorizationCodeData` не будет виден из `AuthorizationController` и grant handlers.

### 1. AuthorizationCodeData — добавить `codeChallengeMethod`

Файл: `repository/AuthorizationCodeStore.kt`

```kotlin
data class AuthorizationCodeData(
    val code: String,
    val clientId: String,
    val userId: UUID,
    val redirectUri: String,
    val scopes: List<String>,
    val codeChallenge: String,
    val codeChallengeMethod: String,   // ← ДОБАВИТЬ (раньше отсутствовал)
    val nonce: String? = null,
    val sessionId: String? = null,
)
```

RFC 7636 требует хранить метод вместе с challenge, чтобы token exchange мог корректно верифицировать `code_verifier`.

### 2. RefreshTokenRepository — исправить тип параметра

Файл: `repository/RefreshTokenRepository.kt`

```kotlin
// Было:
fun revokeAllBySessionId(sessionId: String): Int

// Должно быть:
fun revokeAllBySessionId(sessionId: UUID): Int
```

`RefreshTokenEntity.sessionId` имеет тип `UUID`. Передача `String` вызовет RuntimeException в Hibernate 6+.
`SsoSession.sessionId` хранится как `String` (UUID-строка) — при вызове применять `UUID.fromString(sessionId)`.

### 3. TokenService — email claims в access token

TokenService (существующий, не показан здесь) при выдаче access token **должен** включать claims `email` и `email_verified` если в выданных scope есть `email`. `UserInfoController` читает их из JWT, не из user-service. Если TokenService этого ещё не делает — добавить.

---

## Архитектурные решения

### SecurityConfig — два FilterChain (полная замена существующего файла)

Существующий `SecurityConfig.kt` с одним flat chain заменяется полностью на два `@Bean` с `@Order`:

```
Chain 1 (@Order(1)) — OAuth/OIDC
  securityMatcher: /oauth2/**, /.well-known/**, /userinfo, /actuator/**
  (включает /oauth2/authorize, /oauth2/authorize/callback, /oauth2/token, и т.д.)
  session: STATELESS
  csrf: disabled
  auth: permitAll
        (userinfo и callback проверяют аутентификацию вручную в контроллере)

Chain 2 (@Order(2)) — Admin API
  securityMatcher: /api/v1/**
  session: STATELESS
  csrf: disabled
  auth: oauth2ResourceServer { jwt { decoder = localJwtDecoder() } }
       + hasAuthority("SCOPE_iam:admin")
```

`localJwtDecoder()` — `NimbusJwtDecoder` с RSA публичным ключом из `JwtKeyProvider`. Без внешних вызовов.

### SSO-сессия — интерфейс + Redis-реализация

```kotlin
interface SsoSessionService {
    fun create(userId: UUID, firstClientId: String): SsoSession
    fun get(sessionId: String): SsoSession?
    fun addClient(sessionId: String, clientId: String)  // также обновляет lastActivityAt
    fun updateActivity(sessionId: String)               // обновляет lastActivityAt, не меняет clientIds
    fun delete(sessionId: String)
    fun deleteAllForUser(userId: UUID)
}

class RedisSsoSessionService(...) : SsoSessionService  // текущая реализация
// class RemoteSsoSessionService(...) : SsoSessionService  // будущий session-service
```

Сессия хранится в Redis: `sso:session:{sessionId}` с TTL из `app.ssoSession.ttlSeconds`.
Передаётся через HttpOnly cookie `SSO_SESSION` (значение = sessionId как UUID-строка).
Интерфейс позволяет вынести сессии в отдельный микросервис без изменений в контроллерах.

---

## Новые компоненты

### Сервисы

| Класс | Ответственность |
|---|---|
| `AuthRequestService` | Хранит параметры pending `/oauth2/authorize` в Redis (`auth:request:{uuid}`, TTL из `app.authorizationRequest.ttlSeconds`) |
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
    val sessionId: String,            // UUID as String
    val userId: UUID,
    val clientIds: MutableList<String>,
    val createdAt: Instant,
    val lastActivityAt: Instant,      // обновляется через updateActivity() и addClient()
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

**AuthEvents.kt** — новые события, реализуют `DomainEvent`:

```kotlin
data class TokenIssuedEvent(
    override val eventType: String = "auth.token-issued",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID,
    val clientId: String,
    val grantType: String,
    val scopes: List<String>,
    val sessionId: String?,
) : DomainEvent

data class TokenRevokedEvent(
    override val eventType: String = "auth.token-revoked",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID?,
    val clientId: String,
    val tokenType: String,   // "access_token" | "refresh_token"
    val reason: String,      // "revoked", "user-deleted", "user-disabled", "logout"
) : DomainEvent

data class LoginSuccessEvent(
    override val eventType: String = "auth.login-success",
    override val timestamp: Instant = Instant.now(),
    val userId: UUID,
    val clientId: String,
    val sessionId: String,
    val mfaUsed: Boolean = false,
) : DomainEvent

data class LoginFailedEvent(
    override val eventType: String = "auth.login-failed",
    override val timestamp: Instant = Instant.now(),
    val identifier: String,  // email или username — не хранить пароль
    val clientId: String,
    val reason: String,      // "invalid_credentials", "account_disabled", "account_locked"
) : DomainEvent
```

**SessionEvents.kt**:

```kotlin
data class SessionCreatedEvent(
    override val eventType: String = "session.created",
    override val timestamp: Instant = Instant.now(),
    val sessionId: String,
    val userId: UUID,
    val clientId: String,
) : DomainEvent

data class SessionDestroyedEvent(
    override val eventType: String = "session.destroyed",
    override val timestamp: Instant = Instant.now(),
    val sessionId: String,
    val userId: UUID,
    val reason: String,   // "logout", "logout-all", "user-deleted", "user-disabled"
) : DomainEvent
```

**KafkaTopics.kt** — добавить:
```kotlin
const val AUTH_EVENTS = "iam.auth.events"
const val SESSION_EVENTS = "iam.session.events"
```

**CloudEventEnvelope.kt / DomainEvent** — добавить в `@JsonSubTypes` все 6 новых типов с указанными выше `name`-значениями.

### Конфиг (AppProperties)

```kotlin
data class LoginPageProps(val url: String = "http://localhost:3000")
data class AdminProps(val scope: String = "iam:admin")
data class SsoSessionProps(val ttlSeconds: Long = 86400)              // 24 часа
data class AuthorizationRequestProps(val ttlSeconds: Long = 300)     // 5 минут

// Добавить поля в AppProperties:
val loginPage: LoginPageProps = LoginPageProps()
val admin: AdminProps = AdminProps()
val ssoSession: SsoSessionProps = SsoSessionProps()
val authorizationRequest: AuthorizationRequestProps = AuthorizationRequestProps()
```

### Kafka consumer config

В `application.yaml` (или `KafkaConfig.kt`):
```yaml
spring:
  kafka:
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "org.iamsso.contracts.events"
        spring.json.value.default.type: "org.iamsso.contracts.events.CloudEventEnvelope"
```

Без этой конфигурации Jackson не сможет десериализовать generic-тип `CloudEventEnvelope<DomainEvent>` — `data` превратится в `LinkedHashMap`.

---

## Authorization Flow

### GET /oauth2/authorize

1. Валидация: `client_id` существует, `redirect_uri` зарегистрирован у клиента
   - Ошибки по `client_id` / `redirect_uri` → `400 OAuthError` **без редиректа** (RFC 6749 §4.1.2.1)
2. Валидация параметров: `response_type=code`, `code_challenge` присутствует, `code_challenge_method=S256`
   - PKCE **обязателен для всех клиентов** (и публичных, и конфиденциальных) — платформа не поддерживает implicit flow
   - `response_type` не `code` → редирект с `error=unsupported_response_type`
   - Отсутствие `code_challenge` → редирект с `error=invalid_request`
   - Остальные ошибки → редирект с `error=invalid_request`
3. Валидация scope: запрошенный scope ⊆ зарегистрированных scope клиента → иначе `error=invalid_scope`
4. Проверить cookie `SSO_SESSION` → `SsoSessionService.get(sessionId)` → если сессия валидна:
   - `SsoSessionService.updateActivity(sessionId)`
   - `SsoSessionService.addClient(sessionId, clientId)`
   - Создать auth code (с `codeChallengeMethod` из запроса — PKCE применяется к каждому запросу)
   - `302` на `redirect_uri?code=...&state=...` (silent SSO)
5. Если сессии нет → `AuthRequestService.save(AuthRequest(...))` с TTL `app.authorizationRequest.ttlSeconds`
6. `302` → `{app.loginPage.url}/login?auth_request_id={uuid}`

### POST /oauth2/authorize/callback

Тело `application/x-www-form-urlencoded`: `auth_request_id`, `username`, `password`

> **Threat model:** endpoint принимает учётные данные открытым текстом — в production обязателен TLS.
> Rate limiting и account lockout делегированы user-service (`lockedUntil` в `CredentialsResult`).

1. Загрузить `AuthRequest` из Redis → `400 OAuthError(error=invalid_request)` если не найден или истёк
2. `UserServiceClient.getByEmail()` (если содержит `@`) или `getByUsername()` → найти пользователя
   - Не найден → `302` с `error=invalid_credentials` (см. формат редиректа ниже)
3. Проверить `user.status == ACTIVE` **раньше** проверки пароля → `error=account_disabled`
   - Это предотвращает инкремент счётчика неверных попыток для заблокированных аккаунтов
4. `UserServiceClient.verifyCredentials(userId, password)`:
   - `result.lockedUntil != null` → `LoginFailedEvent` → `error=account_locked`
   - `result.valid == false` → `LoginFailedEvent` → `error=invalid_credentials`
   - Формат редиректа при ошибке: `{loginPage.url}/login?auth_request_id={id}&error={code}`
     (authRequestId **сохраняется** в Redis, пользователь может повторить попытку)
5. `SsoSessionService.create(userId, authRequest.clientId)` → HttpOnly cookie:
   `Set-Cookie: SSO_SESSION={sessionId}; Path=/; HttpOnly; SameSite=Lax`
6. `AuthorizationCodeStore.save(AuthorizationCodeData(codeChallenge=..., codeChallengeMethod=..., sessionId=session.sessionId, ...))` — все поля из `AuthRequest`
7. `AuthRequestService.delete(authRequestId)`
8. `AuthEventPublisher.publish(LoginSuccessEvent(...))` + `SessionCreatedEvent`
9. `302` → `redirect_uri?code={code}&state={state}`

---

## UserInfoController

`GET /userinfo` и `POST /userinfo` (оба метода, OIDC Core §5.3) — Bearer JWT в заголовке `Authorization`

1. Распарсить и верифицировать JWT через `JwtKeyProvider` (RSA публичный ключ, локально) → `401` если невалиден
2. Извлечь `sub` (userId) и `scope` из claims
3. Базовый ответ: всегда `{ sub }`
4. Если scope содержит `email`:
   - Читать `email` и `email_verified` **из JWT claims** (TokenService встраивает их при выдаче — см. "Обязательные правки")
5. Если scope содержит `profile`:
   - Вызвать `UserServiceClient.getProfile(UUID.fromString(sub))` → заполнить `preferred_username`, `name`, `given_name`, `family_name`, `locale`, `picture`

---

## ClientController

Тонкий слой над существующим `ClientService`.

- Защита через Chain 2: Bearer JWT с `SCOPE_iam:admin`
- `POST /api/v1/clients` → `ClientService.register()` → `201 ClientWithSecretResponse`
- `GET /api/v1/clients` → `ClientService.findAll()` → `200 List<ClientResponse>`
- `GET /api/v1/clients/{id}` → `ClientService.findById()` → `200` / `404`
- `PATCH /api/v1/clients/{id}` → `ClientService.update()` → `200 ClientResponse`
- `DELETE /api/v1/clients/{id}` → `ClientService.delete()` + `RefreshTokenRepository.revokeAllByClientId(clientId)` → `204`
- `POST /api/v1/clients/{id}/secret/rotate` → `ClientService.rotateSecret()` → `200 ClientWithSecretResponse`

---

## SessionController

Cookie `SSO_SESSION` содержит только `sessionId` (UUID-строка). `userId` извлекается через `SsoSessionService.get(sessionId)`.

- `GET /api/v1/sessions/current`:
  1. Прочитать cookie `SSO_SESSION` → `sessionId`
  2. `SsoSessionService.get(sessionId)` → `401` если null
  3. `200 SessionResponse` (маппинг включает `lastActivityAt`)

- `POST /api/v1/sessions/logout`:
  1. `SsoSessionService.get(sessionId)` → получить `SsoSession`
  2. `@Transactional`: `RefreshTokenRepository.revokeAllBySessionId(UUID.fromString(sessionId))` — только текущая сессия, не затрагивает параллельные сессии того же пользователя
  3. `SsoSessionService.delete(sessionId)`
  4. `AuthEventPublisher.publish(SessionDestroyedEvent(reason="logout"))`
  5. `Set-Cookie: SSO_SESSION=; Max-Age=0; Path=/`
  6. Если передан `post_logout_redirect_uri` — валидировать против `OAuthClientEntity.redirectUris` (реюз существующего поля для диплома) → редирект; иначе `200`
     > В production потребуется отдельное поле `postLogoutRedirectUris` на клиенте.

- `POST /api/v1/sessions/logout-all`:
  1. `SsoSessionService.get(sessionId)` → получить `userId`
  2. `RefreshTokenRepository.revokeAllByUserId(userId)`
  3. `SsoSessionService.deleteAllForUser(userId)`
  4. `AuthEventPublisher.publish(SessionDestroyedEvent(reason="logout-all"))`
  5. `Set-Cookie: SSO_SESSION=; Max-Age=0; Path=/` → `200`

---

## AuthEventPublisher

Публикует через `KafkaTemplate<String, CloudEventEnvelope<*>>` (ключ = userId.toString() или clientId).

| Событие | Топик | Публикуется в |
|---|---|---|
| `LoginSuccessEvent` | `iam.auth.events` | `AuthorizationController.callback` — успех |
| `LoginFailedEvent` | `iam.auth.events` | `AuthorizationController.callback` — неверный пароль / аккаунт заблокирован |
| `TokenIssuedEvent` | `iam.auth.events` | `TokenController` — успешная выдача |
| `TokenRevokedEvent` | `iam.auth.events` | `RevocationController` |
| `SessionCreatedEvent` | `iam.session.events` | `RedisSsoSessionService.create()` |
| `SessionDestroyedEvent` | `iam.session.events` | logout / logout-all |

---

## UserKafkaConsumer

Десериализация через `@JsonTypeInfo(use = Id.NAME, property = "eventType")` на `DomainEvent` + Kafka consumer config (см. выше). Consumer группа: `auth-service`.

> **@Transactional:** методы `revokeAllByUserId` и `revokeAllBySessionId` помечены `@Modifying` и требуют транзакции.
> Пометить метод `onUserEvent` аннотацией `@Transactional`, либо вынести вызовы репозитория в отдельный `@Transactional` сервис-метод.

```kotlin
@KafkaListener(topics = [KafkaTopics.USER_EVENTS], groupId = "auth-service")
fun onUserEvent(envelope: CloudEventEnvelope<DomainEvent>) {
    when (val event = envelope.data) {
        is UserDeletedEvent -> {
            refreshTokenRepository.revokeAllByUserId(event.userId)
            ssoSessionService.deleteAllForUser(event.userId)
        }
        is UserStatusChangedEvent -> {
            if (event.newStatus != "ACTIVE") {
                refreshTokenRepository.revokeAllByUserId(event.userId)
                ssoSessionService.deleteAllForUser(event.userId)
            }
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
- `postLogoutRedirectUris` как отдельное поле на `OAuthClientEntity` (production-улучшение)
