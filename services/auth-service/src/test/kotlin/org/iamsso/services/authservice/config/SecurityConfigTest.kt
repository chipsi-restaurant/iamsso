package org.iamsso.services.authservice.config

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"])
class SecurityConfigTest {

    @Autowired
    lateinit var wac: WebApplicationContext

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    @Test
    fun `well-known endpoint is public`() {
        // /.well-known/** is in the public chain — security won't block it (404 from no controller is fine)
        val result = mockMvc.get("/.well-known/openid-configuration").andReturn()
        assert(result.response.status != 401 && result.response.status != 403)
    }

    @Test
    fun `api v1 endpoint is permitted (authorization handled by gateway)`() {
        // /api/v1/** is permitAll in the auth-service security config —
        // authorization for these endpoints is handled by API Gateway + Policy Service.
        val result = mockMvc.get("/api/v1/clients").andReturn()
        assert(result.response.status != 401 && result.response.status != 403)
    }
}
