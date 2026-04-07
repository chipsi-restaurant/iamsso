package org.iamsso.services.policyservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.iamsso.services.policyservice.exception.GlobalExceptionHandler
import org.iamsso.services.policyservice.exception.RoleAlreadyExistsException
import org.iamsso.services.policyservice.mapper.CreateRoleRequest
import org.iamsso.services.policyservice.mapper.RoleResponse
import org.iamsso.services.policyservice.service.RoleService
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleControllerTest {

    @Mock lateinit var roleService: RoleService

    private lateinit var mockMvc: MockMvc
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private val roleId = UUID.randomUUID()
    private val sampleRole = RoleResponse(
        id = roleId, name = "viewer", description = "Read-only",
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(RoleController(roleService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
            .build()
    }

    @Test
    fun `GET roles returns 200 list`() {
        whenever(roleService.list()).thenReturn(listOf(sampleRole))
        mockMvc.get("/api/v1/roles")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("viewer") }
            }
    }

    @Test
    fun `POST roles returns 201`() {
        whenever(roleService.create(any())).thenReturn(sampleRole)
        mockMvc.post("/api/v1/roles") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(CreateRoleRequest(name = "viewer"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("viewer") }
        }
    }

    @Test
    fun `POST roles duplicate returns 409`() {
        whenever(roleService.create(any())).thenThrow(RoleAlreadyExistsException("admin"))
        mockMvc.post("/api/v1/roles") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(CreateRoleRequest(name = "admin"))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ROLE_ALREADY_EXISTS") }
        }
    }

    @Test
    fun `DELETE roles returns 204`() {
        mockMvc.delete("/api/v1/roles/$roleId")
            .andExpect { status { isNoContent() } }
    }
}
