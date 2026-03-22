package org.iamsso.services.authservice.config

import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Генерирует и хранит RSA-ключ для подписи JWT.
 *
 * В production ключи должны загружаться из Vault / HSM / файла.
 * Для разработки — генерируем при старте.
 */
@Component
class JwtKeyProvider(private val props: AppProperties) {

    val rsaKey: RSAKey = RSAKeyGenerator(props.jwt.rsaKeySize)
        .keyID(props.jwt.keyId)
        .generate()

    val publicKey: RSAPublicKey = rsaKey.toRSAPublicKey()
    val privateKey: RSAPrivateKey = rsaKey.toRSAPrivateKey()
}