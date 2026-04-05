package org.iamsso.services.authservice.service.grant

import org.iamsso.services.authservice.config.AppProperties
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.entity.RefreshTokenEntity
import org.iamsso.services.authservice.exception.AccessDeniedException
import org.iamsso.services.authservice.exception.AuthorizationPendingException
import org.iamsso.services.authservice.exception.ExpiredTokenException
import org.iamsso.services.authservice.exception.SlowDownException
import org.iamsso.services.authservice.repository.DeviceCodeData
import org.iamsso.services.authservice.repository.DeviceCodeStore
import org.iamsso.services.authservice.repository.RefreshTokenRepository
import org.iamsso.services.authservice.service.AuthEventPublisher
import org.iamsso.services.authservice.service.JwtIssuer
import org.iamsso.services.authservice.service.TokenFamilyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceCodeGrantHandlerTest {

    @Mock lateinit var deviceCodeStore: DeviceCodeStore
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var jwtIssuer: JwtIssuer
    @Mock lateinit var tokenFamilyService: TokenFamilyService
    @Mock lateinit var authEventPublisher: AuthEventPublisher
    @Mock lateinit var redis: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>

    private lateinit var handler: DeviceCodeGrantHandler
    private val props = AppProperties()

    private val clientId = "device-client"
    private val userId = UUID.randomUUID()

    private val client = OAuthClientEntity(
        clientId = clientId, clientName = "Device", clientSecretHash = "h",
        grantTypes = "urn:ietf:params:oauth:grant-type:device_code",
        redirectUris = "", scopes = "openid",
        accessTokenTtlSeconds = 3600, refreshTokenTtlSeconds = 2592000,
    )

    @BeforeEach
    fun setUp() {
        whenever(redis.opsForValue()).thenReturn(valueOps)
        handler = DeviceCodeGrantHandler(deviceCodeStore, refreshTokenRepository, jwtIssuer, tokenFamilyService, authEventPublisher, redis, props)
        whenever(jwtIssuer.issueAccessToken(anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any())).thenReturn("access-token")
        whenever(refreshTokenRepository.save(any<RefreshTokenEntity>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `handle returns tokens when device code approved`() {
        val deviceCode = "dc-1"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "ABCD-1234", clientId = clientId,
                scopes = listOf("openid"), userId = userId, approved = true)
        )
        whenever(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true)
        val response = handler.handle(mapOf("device_code" to deviceCode), client)
        assertNotNull(response.accessToken)
        assertNotNull(response.refreshToken)
    }

    @Test
    fun `handle throws AuthorizationPendingException when not yet approved`() {
        val deviceCode = "dc-2"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "EFGH-5678", clientId = clientId,
                scopes = listOf("openid"), approved = false)
        )
        whenever(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true)
        assertThrows<AuthorizationPendingException> { handler.handle(mapOf("device_code" to deviceCode), client) }
    }

    @Test
    fun `handle throws SlowDownException on rapid polling`() {
        val deviceCode = "dc-3"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "IJKL-9012", clientId = clientId,
                scopes = listOf("openid"), approved = false)
        )
        whenever(valueOps.setIfAbsent(any(), any(), any())).thenReturn(false)
        assertThrows<SlowDownException> { handler.handle(mapOf("device_code" to deviceCode), client) }
    }

    @Test
    fun `handle throws AccessDeniedException when denied`() {
        val deviceCode = "dc-4"
        whenever(deviceCodeStore.findByDeviceCode(deviceCode)).thenReturn(
            DeviceCodeData(deviceCode = deviceCode, userCode = "MNOP-3456", clientId = clientId,
                scopes = listOf("openid"), denied = true)
        )
        assertThrows<AccessDeniedException> { handler.handle(mapOf("device_code" to deviceCode), client) }
    }

    @Test
    fun `handle throws ExpiredTokenException when device code not found`() {
        whenever(deviceCodeStore.findByDeviceCode("unknown")).thenReturn(null)
        assertThrows<ExpiredTokenException> { handler.handle(mapOf("device_code" to "unknown"), client) }
    }

    @Test
    fun `supports returns true only for device_code grant`() {
        assert(handler.supports("urn:ietf:params:oauth:grant-type:device_code"))
        assert(!handler.supports("authorization_code"))
    }
}
