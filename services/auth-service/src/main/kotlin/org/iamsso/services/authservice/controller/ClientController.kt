package org.iamsso.services.authservice.controller

import org.iamsso.contracts.auth.model.ClientResponse
import org.iamsso.contracts.auth.model.ClientWithSecretResponse
import org.iamsso.contracts.auth.model.RegisterClientRequest
import org.iamsso.contracts.auth.model.UpdateClientRequest
import org.iamsso.services.authservice.service.ClientService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/clients")
class ClientController(private val clientService: ClientService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterClientRequest): ClientWithSecretResponse =
        clientService.register(request)

    @GetMapping
    fun list(): List<ClientResponse> = clientService.findAll()

    @GetMapping("/{clientId}")
    fun get(@PathVariable clientId: String): ClientResponse =
        clientService.findById(clientId)

    @PatchMapping("/{clientId}")
    fun update(@PathVariable clientId: String, @RequestBody request: UpdateClientRequest): ClientResponse =
        clientService.update(clientId, request)

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable clientId: String) = clientService.delete(clientId)

    @PostMapping("/{clientId}/secret/rotate")
    fun rotateSecret(@PathVariable clientId: String): ClientWithSecretResponse =
        clientService.rotateSecret(clientId)
}
