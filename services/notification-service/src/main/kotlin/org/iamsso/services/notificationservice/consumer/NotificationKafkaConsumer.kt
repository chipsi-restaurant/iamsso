package org.iamsso.services.notificationservice.consumer

import org.iamsso.contracts.events.KafkaTopics.NOTIFICATION_COMMANDS
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.stereotype.Component

@Component
class NotificationKafkaConsumer {

    @RetryableTopic(
        attempts = "3",
    )
    @KafkaListener(topics = [NOTIFICATION_COMMANDS], groupId = "notification-service")
    fun listen(message: String) {
        println(message)
    }
}