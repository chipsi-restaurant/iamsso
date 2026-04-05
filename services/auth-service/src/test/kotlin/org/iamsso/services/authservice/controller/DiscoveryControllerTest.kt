package org.iamsso.services.authservice.controller

import org.iamsso.services.authservice.config.AppProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DiscoveryControllerTest {

    private lateinit var mockMvc: MockMvc
    private val props = AppProperties(issuer = "http://localhost:8080")

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(DiscoveryController(props)).build()
    }

    @Test
    fun `GET openid-configuration returns required OIDC Discovery fields`() {
        mockMvc.get("/.well-known/openid-configuration")
            .andExpect {
                status { isOk() }
                jsonPath("$.issuer") { value("http://localhost:8080") }
                jsonPath("$.authorization_endpoint") { value("http://localhost:8080/oauth2/authorize") }
                jsonPath("$.token_endpoint") { value("http://localhost:8080/oauth2/token") }
                jsonPath("$.userinfo_endpoint") { value("http://localhost:8080/userinfo") }
                jsonPath("$.jwks_uri") { value("http://localhost:8080/.well-known/jwks.json") }
                jsonPath("$.revocation_endpoint") { value("http://localhost:8080/oauth2/revoke") }
                jsonPath("$.introspection_endpoint") { value("http://localhost:8080/oauth2/introspect") }
                jsonPath("$.device_authorization_endpoint") { value("http://localhost:8080/oauth2/device_authorization") }
                jsonPath("$.grant_types_supported") { isArray() }
                jsonPath("$.id_token_signing_alg_values_supported[0]") { value("RS256") }
            }
    }
}
