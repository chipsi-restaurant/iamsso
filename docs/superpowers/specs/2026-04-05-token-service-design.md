# TokenService + Token Endpoints — Дизайн

**Дата:** 2026-04-05
**Статус:** Approved
**Область:** `services/auth-service`

---

## Контекст

Auth-service уже реализует: authorize flow, userinfo, client CRUD, session management, Kafka events. Отсутствуют: TokenService (выдача JWT), TokenController (`/oauth2/token`), RevocationController, IntrospectionController, DeviceController, DiscoveryController, JwksController (заполнить stub).

---

## Grant Types

Все четыре:
- `authorization_code` с обязательным PKCE (S256)
- `refresh_token` с rotation + reuse detection
- `client_credentials`
- `urn:ietf:params:oauth:grant-type:device_code`

---

## Архитектура — Grant Handler Pattern

```
TokenController
  └─ TokenService
       ├─ authenticateClient()         — Basic header или form params
       └─ handlers: List<GrantHandler>
            ├─ AuthCodeGrantHandler
            ├─ RefreshTokenGrantHandler
            ├─ ClientCredentialsGrantHandler
            └─ DeviceCodeGrantHandler

JwtIssuer          — подпись JWT через Nimbus + JwtKeyProvider
TokenFamilyService — reuse detection в Redis
```

---

## Файловая карта

### New — services/auth-service

```
service/
  JwtIssuer.kt
  TokenFamilyService.kt
  GrantHandler.kt                  (interface)
  grant/
    AuthCodeGrantHandler.kt
    RefreshTokenGrantHandler.kt
    ClientCredentialsGrantHandler.kt
    DeviceCodeGrantHandler.kt
  TokenService.kt

controller/
  TokenController.kt               POST /oauth2/token
  RevocationController.kt          POST /oauth2/revoke
  IntrospectionController.kt       POST /oauth2/introspect
  DeviceController.kt              POST /oauth2/device_authorization
  DiscoveryController.kt           GET  /.well-known/openid-configuration
  JwksController.kt                GET  /.well-known/jwks.json  (заполнить stub)
```

### Modified — services/auth-service

```
config/AppConfig.kt                — добавить tokens.refreshTokenTtlSeconds (глобальный дефолт)
config/SecurityConfig.kt           — добавить новые endpoints в Chain 1
src/main/resources/application.yaml — добавить app.tokens.refresh-token-ttl-seconds
```

### New — DB migration

```
src/main/resources/db/changelog/changelogs/002-add-family-id.yaml
```

Добавляет колонку `family_id UUID` (nullable) + индекс в таблицу `refresh_tokens`.

### Незакоммиченные файлы (закоммитить до начала)

```
entity/OAuthClientEntity.kt
entity/RefreshTokenEntity.kt
exception/OAuthExceptions.kt
exception/OAuthExceptionHandler.kt
repository/DeviceCodeStore.kt
repository/OAuthClientRepository.kt
controller/JwksController.kt       (пустой stub — будет заменён)
src/main/resources/db/             (миграция 001)
AuthServiceApplication.kt         (diff: @ConfigurationPropertiesScan)
src/test/resources/application-test.yaml
```

---

## Аутентификация клиента

Публичный метод `TokenService.authenticateClient(params, request)` — четыре контроллера инжектируют `TokenService` и вызывают его напрямую:

1. Проверить `Authorization: Basic` header → base64 decode → `clientId:clientSecret`
   ИЛИ `client_id` + `client_secret` из тела формы
2. `OAuthClientRepository.findById(clientId)` → `InvalidClientException` если не найден
3. `BCrypt.checkpw(secret, entity.clientSecretHash)` → `InvalidClientException` если неверно
4. Дополнительно проверить `previousSecretHash` + `previousSecretExpiresAt` (grace period при ротации секрета)

---

## Grant Handlers

### AuthCodeGrantHandler

```
1. AuthorizationCodeStore.consume(code) → null → invalid_grant
2. code.clientId == params.client_id, code.redirectUri == params.redirect_uri → invalid_grant
3. PKCE: base64url(SHA-256(code_verifier)) == code.codeChallenge → invalid_grant
4. JwtIssuer.issueAccessToken(userId, clientId, scopes, sessionId)
5. Если "openid" ∈ scopes → JwtIssuer.issueIdToken(userId, clientId, nonce, scopes)
   (profile claims: UserServiceClient.getProfile() если "profile" ∈ scopes)
6. RefreshTokenRepository.save(new token с familyId = UUID.randomUUID())
7. TokenFamilyService.initFamily(familyId, tokenHash)
8. AuthEventPublisher.publish(TokenIssuedEvent)
9. Вернуть TokenResponse
```

### RefreshTokenGrantHandler

```
1. RefreshTokenRepository.findByTokenHash(SHA-256(token)) → null → invalid_grant
2. token.clientId == client.clientId → invalid_grant
3. token.revoked → TokenFamilyService.revokeFamily(familyId) → invalid_grant  ← reuse detection
4. token.isExpired → invalid_grant
5. token.revoked = true, token.replacedBy = newId → save
6. Выдать новый access token + новый RefreshTokenEntity (с тем же familyId)
7. TokenFamilyService.addToFamily(familyId, newHash)
8. TokenIssuedEvent
```

### ClientCredentialsGrantHandler

```
1. scope ⊆ client.scopes → invalid_scope
2. JwtIssuer.issueAccessToken(sub=clientId, scopes) — userId=null, sessionId=null
3. Нет refresh token
4. TokenIssuedEvent
```

### DeviceCodeGrantHandler

```
1. DeviceCodeStore.findByDeviceCode(device_code) → null/expired → expired_token
2. denied → access_denied
3. !approved → authorization_pending
   (slow_down если интервал между запросами < app.deviceCode.pollingIntervalSeconds)
4. Выдать access + refresh token (как в AuthCode, без id_token / nonce)
5. DeviceCodeStore.delete()
6. TokenIssuedEvent
```

---

## JWT структура

### Access Token

```json
{
  "iss": "<app.issuer>",
  "sub": "<userId | clientId>",
  "aud": "<clientId>",
  "iat": 0,
  "exp": 0,
  "jti": "<UUID>",
  "scope": "openid email profile",
  "sid": "<sessionId>",
  "email": "user@example.com",
  "email_verified": true
}
```

- `sid` и `email`/`email_verified` включаются только если присутствуют (scope-зависимо)
- TTL: `client.accessTokenTtlSeconds` (переопределяет `app.jwt`)
- Подписывается RSA-256 ключом из `JwtKeyProvider`

### ID Token (только если `openid` ∈ scope)

```json
{
  "iss": "<app.issuer>",
  "sub": "<userId>",
  "aud": "<clientId>",
  "iat": 0,
  "exp": 0,
  "nonce": "...",
  "email": "...",
  "name": "...",
  "preferred_username": "..."
}
```

- Profile claims (`name`, `preferred_username`, etc.) — только если `profile` ∈ scope, через `UserServiceClient.getProfile()`
- TTL: `app.jwt.idTokenTtlSeconds`

---

## TokenFamilyService

Redis-структура: `auth:family:{familyId}` → `Set<tokenHash>`, TTL = максимальный `refreshTokenTtlSeconds` клиента.

```kotlin
interface TokenFamilyService {
    fun initFamily(familyId: UUID, tokenHash: String, ttl: Duration)
    fun addToFamily(familyId: UUID, tokenHash: String)
    fun revokeFamily(familyId: UUID)   // revoke all hashes in family via RefreshTokenRepository
}
```

`revokeFamily` итерирует set и для каждого hash вызывает `refreshTokenRepository.revokeByTokenHash(hash)`. Нужно добавить в `RefreshTokenRepository`:
```kotlin
@Modifying
@Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.tokenHash = :hash")
fun revokeByTokenHash(hash: String): Int
```
O(n) по размеру семьи (обычно ≤ 10 токенов за жизнь сессии).

`familyId` хранится в `RefreshTokenEntity` как новая колонка (миграция 002, nullable).

---

## Контроллеры

### TokenController — POST /oauth2/token

`Content-Type: application/x-www-form-urlencoded`

Ответ при успехе:
```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "id_token": "...",
  "scope": "openid email"
}
```

Поля `refresh_token` и `id_token` — только если применимо к grant type.

### RevocationController — POST /oauth2/revoke

1. Аутентификация клиента
2. `SHA-256(token)` → `RefreshTokenRepository.findByTokenHash()`
3. Если найден и `token.clientId == client.clientId` → `token.revoked = true`
4. RFC 7009: **всегда `200 OK`**, даже если токен не найден или уже отозван

### IntrospectionController — POST /oauth2/introspect

1. Аутентификация клиента
2. Попытка распарсить как JWT → `JwtIssuer.verify()`
   - Невалидный / expired → `{"active": false}`
   - Валидный → вернуть claims + `"active": true`
3. Если не JWT → попытка найти как refresh token через hash
   - Найден и `isActive` → `{"active": true, "scope": "...", "sub": "...", "exp": ...}`
   - Иначе → `{"active": false}`

### DeviceController — POST /oauth2/device_authorization

1. Аутентификация клиента
2. Scope ⊆ client.scopes → invalid_scope
3. Сгенерировать `device_code` (UUID) + `user_code` (8 символов, A-Z0-9, без похожих символов)
4. `DeviceCodeStore.save(DeviceCodeData(clientId, scopes))`
5. Ответ:
```json
{
  "device_code": "...",
  "user_code": "ABCD-1234",
  "verification_uri": "<app.loginPage.url>/device",
  "expires_in": 600,
  "interval": 5
}
```

### DiscoveryController — GET /.well-known/openid-configuration

Статический JSON из `AppProperties.issuer`. Поля по OIDC Discovery spec:
`issuer`, `authorization_endpoint`, `token_endpoint`, `userinfo_endpoint`, `jwks_uri`,
`revocation_endpoint`, `introspection_endpoint`, `device_authorization_endpoint`,
`scopes_supported`, `response_types_supported`, `grant_types_supported`,
`token_endpoint_auth_methods_supported`, `id_token_signing_alg_values_supported`.

### JwksController — GET /.well-known/jwks.json

```kotlin
@GetMapping("/.well-known/jwks.json")
fun jwks(): Map<String, Any> {
    val jwkSet = JWKSet(jwtKeyProvider.rsaKey.toPublicJWK())
    return jwkSet.toJSONObject()
}
```

---

## SecurityConfig — изменения

Добавить в Chain 1 (`securityMatcher`):
```
/oauth2/token, /oauth2/revoke, /oauth2/introspect,
/oauth2/device_authorization, /.well-known/jwks.json
```

(`.well-known/**` уже покрыт, `device_authorization` — добавить явно)

---

## Тесты

### Unit (service/)

| Класс | Что покрыть |
|---|---|
| `JwtIssuerTest` | access token claims, id_token claims, подпись верифицируется публичным ключом |
| `TokenFamilyServiceTest` | initFamily, addToFamily, revokeFamily через RedisTemplate mock |
| `AuthCodeGrantHandlerTest` | happy path, invalid code, PKCE fail, expired, scope mismatch |
| `RefreshTokenGrantHandlerTest` | rotation happy path, reuse detection → revoke family, expired |
| `ClientCredentialsGrantHandlerTest` | happy path, invalid scope |
| `DeviceCodeGrantHandlerTest` | pending, approved, denied, expired, slow_down |

### Integration (controller/)

| Класс | Ключевые сценарии |
|---|---|
| `TokenControllerTest` | все 4 grant types × happy path + основные ошибки, Basic auth + form auth |
| `RevocationControllerTest` | revoke успешный, токен не найден → 200 |
| `IntrospectionControllerTest` | активный JWT, expired JWT, неизвестный → active:false |
| `DeviceControllerTest` | выдача device_code, invalid scope |
| `DiscoveryControllerTest` | наличие обязательных полей в JSON |

---

## Что не входит в этот этап

- ГОСТ-ключи (JwksController возвращает только RSA ключ)
- MFA в token flow
- Consent flow
- Persistent sessions (SSO-сессии остаются в Redis)
- `postLogoutRedirectUris` как отдельное поле
