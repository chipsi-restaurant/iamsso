package org.iamsso.services.mfaservice

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["iam.mfa.events", "iam.notification.commands"],
)
class MfaServiceApplicationTests {
    @Test
    fun contextLoads() {}
}
