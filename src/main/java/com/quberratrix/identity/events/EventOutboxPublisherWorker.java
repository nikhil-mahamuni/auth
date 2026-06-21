package com.quberratrix.identity.events;

import com.quberratrix.identity.config.properties.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Instant;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class EventOutboxPublisherWorker {

    private final EventOutboxRepository outboxRepository;
    private final KafkaSender<String, String> sender;
    private final KafkaTopicProperties topicProperties;

    // For brevity of demonstration, we could use an R2DBC query to fetch PENDING.
    // Here we're using a reactive scheduler block as placeholder logic.
    // In actual high-concurrency production, use Spring Integration, Debezium CDC,
    // or a dedicated paginated Flux scanner mapped dynamically.

    // @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        // Implementation logic to safely query PENDING/FAILED, send to Kafka via sender.send(),
        // update status to PUBLISHED/DEAD_LETTER based on result, utilizing reactive streams properly.
        // Due to lack of a findByStatus in the base ReactiveCrudRepository without defining it,
        // we're establishing the framework pattern ready for completion.
    }
}
