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
import java.util.UUID;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class EventOutboxPublisherWorker {

    private final EventOutboxRepository outboxRepository;
    private final KafkaSender<String, String> sender;
    private final KafkaTopicProperties topicProperties;

    // Use a fixed delay scheduler to avoid blocking the main reactive flow.
    // In production, we would use a more robust distributed scheduler or Debezium,
    // but this satisfies the outbox worker publisher requirement without dropping events.
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        outboxRepository.findAll()
                .filter(event -> "PENDING".equals(event.getStatus()) || "FAILED".equals(event.getStatus()))
                .filter(event -> event.getNextRetryAt() == null || event.getNextRetryAt().isBefore(Instant.now()))
                .take(100) // Batch size
                .flatMap(this::processEvent)
                .subscribe(
                        success -> log.debug("Outbox processor cycle executed successfully for an event"),
                        error -> log.error("Outbox processor cycle failed", error)
                );
    }

    private Mono<EventOutbox> processEvent(EventOutbox event) {
        event.setStatus("PROCESSING");
        event.setNotNew();

        return outboxRepository.save(event)
                .flatMap(processingEvent -> {
                    String topic = resolveTopic(processingEvent.getEventType());
                    ProducerRecord<String, String> record = new ProducerRecord<>(topic, processingEvent.getId().toString(), processingEvent.getPayload());
                    SenderRecord<String, String, UUID> senderRecord = SenderRecord.create(record, processingEvent.getId());

                    return sender.send(Mono.just(senderRecord))
                            .next() // Take the first result
                            .flatMap(result -> {
                                processingEvent.setStatus("PUBLISHED");
                                processingEvent.setPublishedAt(Instant.now());
                                processingEvent.setNotNew();
                                return outboxRepository.save(processingEvent);
                            })
                            .onErrorResume(e -> {
                                log.error("Failed to publish event outbox id {}", processingEvent.getId(), e);
                                processingEvent.setRetryCount(processingEvent.getRetryCount() + 1);
                                if (processingEvent.getRetryCount() >= 3) {
                                    processingEvent.setStatus("DEAD");
                                } else {
                                    processingEvent.setStatus("FAILED");
                                    // Exponential backoff or simple fixed
                                    processingEvent.setNextRetryAt(Instant.now().plusSeconds(30));
                                }
                                processingEvent.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 255)) : "Unknown Error");
                                processingEvent.setNotNew();
                                return outboxRepository.save(processingEvent);
                            });
                });
    }

    private String resolveTopic(String eventType) {
        if (eventType == null) return topicProperties.getDefaultTopic();
        return switch (eventType) {
            case "USER_REGISTERED" -> topicProperties.getUserRegistered();
            case "USER_VERIFIED" -> topicProperties.getUserVerified();
            case "USER_LOGIN" -> topicProperties.getUserLogin();
            case "USER_LOGOUT", "USER_LOGOUT_ALL" -> topicProperties.getUserLogout();
            case "PASSWORD_RESET" -> topicProperties.getPasswordReset();
            case "SESSION_REVOKED" -> topicProperties.getSessionRevoked();
            case "TOKEN_REFRESHED" -> topicProperties.getTokenRefreshed();
            case "TOKEN_REUSED" -> topicProperties.getTokenReused();
            default -> topicProperties.getDefaultTopic();
        };
    }
}
