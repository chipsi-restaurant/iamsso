package org.iamsso.services.policyservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.policyservice.exception.GlobalExceptionHandler
import org.iamsso.services.policyservice.mapper.EvaluateRequest
import org.iamsso.services.policyservice.mapper.EvaluateResponse
import org.iamsso.services.policyservice.mapper.SubjectInfo
import org.iamsso.services.policyservice.service.PolicyEvaluator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluateControllerTest {

    @Mock lateinit var evaluator: PolicyEvaluator

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(EvaluateController(evaluator))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `POST evaluate returns allowed true`() {
        val policyId = UUID.randomUUID()
        whenever(evaluator.evaluate(any())).thenReturn(
            EvaluateResponse(allowed = true, policyId = policyId, reason = "Policy 'Admin' allowed")
        )
        mockMvc.post("/api/v1/policy/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(EvaluateRequest(
                subject = SubjectInfo(userId = UUID.randomUUID().toString(), role = "admin"),
                action = "READ", resource = "orders/123",
            ))
        }.andExpect {
            status { isOk() }
            jsonPath("$.allowed") { value(true) }
            jsonPath("$.policyId") { value(policyId.toString()) }
        }
    }

    @Test
    fun `POST evaluate returns denied`() {
        whenever(evaluator.evaluate(any())).thenReturn(
            EvaluateResponse(allowed = false, reason = "No matching policy found, deny by default")
        )
        mockMvc.post("/api/v1/policy/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(EvaluateRequest(
                subject = SubjectInfo(userId = UUID.randomUUID().toString(), role = "user"),
                action = "DELETE", resource = "orders/123",
            ))
        }.andExpect {
            status { isOk() }
            jsonPath("$.allowed") { value(false) }
            jsonPath("$.policyId") { doesNotExist() }
        }
    }
}
