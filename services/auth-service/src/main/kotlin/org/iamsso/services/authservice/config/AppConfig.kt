package org.iamsso.services.authservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val issuer: String = "http://localhost:8080",
    val jwt: JwtProps = JwtProps(),
    val tokens: TokensProps = TokensProps(),
    val authorizationCode: AuthCodeProps = AuthCodeProps(),
    val deviceCode: DeviceCodeProps = DeviceCodeProps(),
    val userService: UserServiceProps = UserServiceProps(),
    val loginPage: LoginPageProps = LoginPageProps(),
    val admin: AdminProps = AdminProps(),
    val ssoSession: SsoSessionProps = SsoSessionProps(),
    val authorizationRequest: AuthorizationRequestProps = AuthorizationRequestProps(),
) {
    data class JwtProps(
        val accessTokenTtlSeconds: Long = 3600,
        val idTokenTtlSeconds: Long = 3600,
        val keyId: String = "iam-rsa-1",
        val rsaKeySize: Int = 2048,
    )
    data class TokensProps(val refreshTokenTtlSeconds: Long = 2592000)
    data class AuthCodeProps(val ttlSeconds: Long = 60)
    data class DeviceCodeProps(
        val ttlSeconds: Long = 600,
        val pollingIntervalSeconds: Int = 5,
        val userCodeLength: Int = 8,
    )
    data class UserServiceProps(val baseUrl: String = "http://localhost:8081")
    data class LoginPageProps(val url: String = "http://localhost:3000")
    data class AdminProps(val scope: String = "iam:admin")
    data class SsoSessionProps(val ttlSeconds: Long = 86400)
    data class AuthorizationRequestProps(val ttlSeconds: Long = 300)
}
