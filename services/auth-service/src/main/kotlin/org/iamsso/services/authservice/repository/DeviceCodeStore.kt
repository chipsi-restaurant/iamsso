package org.iamsso.services.authservice.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.iamsso.services.authservice.config.AppProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

data class DeviceCodeData(
    val deviceCode: String,
    val userCode: String,
    val clientId: String,
    val scopes: List<String>,
    var userId: UUID? = null,
    var approved: Boolean = false,
    var denied: Boolean = false,
)

@Repository
class DeviceCodeStore(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val props: AppProperties,
) {
    private val devicePrefix = "auth:device:"
    private val userCodePrefix = "auth:usercode:"

    fun save(data: DeviceCodeData) {
        val ttl = Duration.ofSeconds(props.deviceCode.ttlSeconds.toLong())
        val json = objectMapper.writeValueAsString(data)
        redis.opsForValue().set("$devicePrefix${data.deviceCode}", json, ttl)
        redis.opsForValue().set("$userCodePrefix${data.userCode}", data.deviceCode, ttl)
    }

    fun findByDeviceCode(deviceCode: String): DeviceCodeData? {
        val json = redis.opsForValue().get("$devicePrefix$deviceCode") ?: return null
        return objectMapper.readValue(json, DeviceCodeData::class.java)
    }

    fun findByUserCode(userCode: String): DeviceCodeData? {
        val deviceCode = redis.opsForValue().get("$userCodePrefix$userCode") ?: return null
        return findByDeviceCode(deviceCode)
    }

    fun update(data: DeviceCodeData) {
        val key = "$devicePrefix${data.deviceCode}"
        val ttl = redis.getExpire(key)
        val json = objectMapper.writeValueAsString(data)
        if (ttl > 0) {
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttl))
        }
    }

    fun delete(data: DeviceCodeData) {
        redis.delete("$devicePrefix${data.deviceCode}")
        redis.delete("$userCodePrefix${data.userCode}")
    }
}