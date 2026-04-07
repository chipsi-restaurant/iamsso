package org.iamsso.services.policyservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.policyservice.entity.PolicyEffect
import org.iamsso.services.policyservice.exception.GlobalExceptionHandler
import org.iamsso.services.policyservice.exception.PolicyNotFoundException
import org.iamsso.services.policyservice.mapper.CreatePolicyRequest
import org.iamsso.services.policyservice.mapper.PolicyResponse
import org.iamsso.services.policyservice.mapper.UpdatePolicyRequest
import org.iamsso.services.policyservice.service.PolicyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyControllerTest {

    @Mock lateinit var policyService: PolicyService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val policyId = UUID.randomUUID()
    private val samplePolicy = PolicyResponse(
        id = policyId, name = "Admin read all", description = null,
        role = "admin", effect = PolicyEffect.ALLOW, action = "READ",
        resourcePattern = "*", conditions = null, priority = 0,
        enabled = true, createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(PolicyController(policyService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST policies returns 201`() {
        whenever(policyService.create(any())).thenReturn(samplePolicy)
        mockMvc.post("/api/v1/policies") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(CreatePolicyRequest(
                name = "Admin read all", role = "admin",
                effect = PolicyEffect.ALLOW, action = "READ", resourcePattern = "*",
            ))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("Admin read all") }
        }
    }

    @Test
    fun `GET policies by id returns 200`() {
        whenever(policyService.getById(policyId)).thenReturn(samplePolicy)
        mockMvc.get("/api/v1/policies/$policyId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(policyId.toString()) }
            }
    }

    @Test
    fun `GET policies by id not found returns 404`() {
        whenever(policyService.getById(policyId)).thenThrow(PolicyNotFoundException(policyId))
        mockMvc.get("/api/v1/policies/$policyId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("POLICY_NOT_FOUND") }
            }
    }

    @Test
    fun `PUT policies returns 200`() {
        whenever(policyService.update(eq(policyId), any())).thenReturn(samplePolicy)
        mockMvc.put("/api/v1/policies/$policyId") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(UpdatePolicyRequest(priority = 10))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `DELETE policies returns 204`() {
        mockMvc.delete("/api/v1/policies/$policyId")
            .andExpect { status { isNoContent() } }
    }
}
