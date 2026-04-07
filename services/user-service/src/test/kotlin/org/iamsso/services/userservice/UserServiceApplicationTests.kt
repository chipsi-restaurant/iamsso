package org.iamsso.services.userservice

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["iam.user.events", "iam.credentials.events", "iam.mfa.events", "iam.notification.commands"],
)
class UserServiceApplicationTests {

    @Test
    fun contextLoads() {}
}
