package org.iamsso.services.notificationservice.config

import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory

@Configuration
class KafkaConfig {

    @Bean
    fun kafkaListenerContainerFactory(
        defaultConsumerFactory: ConsumerFactory<String, String>
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val properties = defaultConsumerFactory
            .configurationProperties
            .toMutableMap()
            .apply {
                this[KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
                this[VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
            }

        @Suppress("UsePropertyAccessSyntax")
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(DefaultKafkaConsumerFactory(properties))
        }
    }
}