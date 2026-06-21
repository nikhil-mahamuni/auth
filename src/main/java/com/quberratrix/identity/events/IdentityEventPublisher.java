package com.quberratrix.identity.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class IdentityEventPublisher {

    private final KafkaSender<String, String> sender;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name}")
    private String applicationName;

    private static final String TOPIC_NAME = "identity-events";

    public IdentityEventPublisher(KafkaSender<String, String> sender, ObjectMapper objectMapper) {
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> publishEvent(String eventType, UUID actorUserId, UUID targetUserId, UUID clientId, UUID sessionId, String ipAddress, String userAgent, String correlationId, String requestId, Map<String, Object> payload) {
        IdentityEventEnvelope envelope = IdentityEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventVersion("v1")
                .occurredAt(Instant.now())
                .producer(applicationName)
                .correlationId(correlationId)
                .requestId(requestId)
                .actorUserId(actorUserId != null ? actorUserId.toString() : null)
                .targetUserId(targetUserId != null ? targetUserId.toString() : null)
                .clientId(clientId != null ? clientId.toString() : null)
                .sessionId(sessionId != null ? sessionId.toString() : null)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .payload(payload)
                .build();

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(envelope))
                .flatMap(json -> {
                    ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, envelope.getEventId(), json);
                    SenderRecord<String, String, String> senderRecord = SenderRecord.create(record, envelope.getEventId());

                    return sender.send(Mono.just(senderRecord))
                            .doOnError(e -> log.error("Failed to publish event {}", eventType, e))
                            .then();
                })
                .onErrorResume(e -> {
                    log.error("Serialization or sending failed for event: " + eventType, e);
                    return Mono.empty();
                });
    }
}
