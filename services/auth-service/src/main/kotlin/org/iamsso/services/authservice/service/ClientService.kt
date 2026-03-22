package org.iamsso.services.authservice.service

import org.iamsso.contracts.auth.model.ClientResponse
import org.iamsso.contracts.auth.model.ClientWithSecretResponse
import org.iamsso.contracts.auth.model.RegisterClientRequest
import org.iamsso.contracts.auth.model.UpdateClientRequest
import org.iamsso.services.authservice.entity.OAuthClientEntity
import org.iamsso.services.authservice.repository.OAuthClientRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ClientService(
    private val clientRepository: OAuthClientRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun register(request: RegisterClientRequest): ClientWithSecretResponse {
        if (clientRepository.existsByClientName(request.clientName))
            throw ResponseStatusException(HttpStatus.CONFLICT, "Client name already exists")
        val secret = UUID.randomUUID().toString()
        val entity = OAuthClientEntity(
            clientId = UUID.randomUUID().toString(),
            clientName = request.clientName,
            clientSecretHash = passwordEncoder.encode(secret) ?: "",
            grantTypes = request.grantTypes.joinToString(",") { it.value },
            redirectUris = request.redirectUris.joinToString(",") { it.toString() },
            scopes = request.scopes?.joinToString(",") ?: "openid",
            tokenEndpointAuthMethod = request.tokenEndpointAuthMethod?.value ?: "client_secret_post",
            accessTokenTtlSeconds = request.accessTokenTtlSeconds ?: 3600,
            refreshTokenTtlSeconds = request.refreshTokenTtlSeconds ?: 2592000,
            deviceCodeTtlSeconds = request.deviceCodeTtlSeconds ?: 600,
        )
        val saved = clientRepository.save(entity)
        return saved.toWithSecretResponse(secret)
    }

    fun findAll(): List<ClientResponse> = clientRepository.findAll().map { it.toResponse() }

    fun findById(clientId: String): ClientResponse =
        clientRepository.findById(clientId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")
        }.toResponse()

    fun update(clientId: String, request: UpdateClientRequest): ClientResponse {
        val entity = clientRepository.findById(clientId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")
        }
        request.clientName?.let { entity.clientName = it }
        request.redirectUris?.let { entity.redirectUris = it.joinToString(",") { uri -> uri.toString() } }
        request.scopes?.let { entity.scopes = it.joinToString(",") }
        request.grantTypes?.let { entity.grantTypes = it.joinToString(",") }
        request.accessTokenTtlSeconds?.let { entity.accessTokenTtlSeconds = it }
        request.refreshTokenTtlSeconds?.let { entity.refreshTokenTtlSeconds = it }
        return clientRepository.save(entity).toResponse()
    }

    fun delete(clientId: String) {
        if (!clientRepository.existsById(clientId))
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")
        clientRepository.deleteById(clientId)
    }

    fun rotateSecret(clientId: String): ClientWithSecretResponse {
        val entity = clientRepository.findById(clientId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found")
        }
        val newSecret = UUID.randomUUID().toString()
        entity.previousSecretHash = entity.clientSecretHash
        entity.clientSecretHash = passwordEncoder.encode(newSecret) ?: ""
        return clientRepository.save(entity).toWithSecretResponse(newSecret)
    }

    private fun OAuthClientEntity.toResponse() = ClientResponse(
        clientId = clientId,
        clientName = clientName,
        grantTypes = grantTypeList(),
        redirectUris = redirectUriList(),
        scopes = scopeList(),
        tokenEndpointAuthMethod = tokenEndpointAuthMethod,
        accessTokenTtlSeconds = accessTokenTtlSeconds,
        refreshTokenTtlSeconds = refreshTokenTtlSeconds,
    )

    private fun OAuthClientEntity.toWithSecretResponse(secret: String) = ClientWithSecretResponse(
        clientId = clientId,
        clientName = clientName,
        grantTypes = grantTypeList(),
        redirectUris = redirectUriList(),
        scopes = scopeList(),
        tokenEndpointAuthMethod = tokenEndpointAuthMethod,
        accessTokenTtlSeconds = accessTokenTtlSeconds,
        refreshTokenTtlSeconds = refreshTokenTtlSeconds,
        clientSecret = secret,
    )
}
