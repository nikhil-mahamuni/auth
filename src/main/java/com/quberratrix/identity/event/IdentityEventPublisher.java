package com.quberratrix.identity.event;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    public Mono<Void> publishEvent(String eventType, UUID actorUserId, UUID targetUserId, UUID clientId, UUID sessionId, Map<String, Object> payload) {
        IdentityEventEnvelope envelope = new IdentityEventEnvelope();
        envelope.setEventId(UUID.randomUUID().toString());
        envelope.setEventType(eventType);
        envelope.setEventVersion("v1");
        envelope.setOccurredAt(Instant.now());
        envelope.setProducer(applicationName);
        // In a real scenario, extract correlationId/requestId from context
        envelope.setCorrelationId(UUID.randomUUID().toString());
        envelope.setRequestId(UUID.randomUUID().toString());

        envelope.setActorUserId(actorUserId != null ? actorUserId.toString() : null);
        envelope.setTargetUserId(targetUserId != null ? targetUserId.toString() : null);
        envelope.setClientId(clientId != null ? clientId.toString() : null);
        envelope.setSessionId(sessionId != null ? sessionId.toString() : null);
        // Note: IP Address and User Agent should ideally come from ServerWebExchange

        envelope.setPayload(payload);

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
                    // Decide if we want to fail the transaction or just log it
                    // Returning Mono.empty() to not break the flow, but in strict systems might want Mono.error
                    return Mono.empty();
                });
    }

    @lombok.Data
    public static class IdentityEventEnvelope {
        private String eventId;
        private String eventType;
        private String eventVersion;
        private Instant occurredAt;
        private String producer;
        private String correlationId;
        private String requestId;
        private String actorUserId;
        private String targetUserId;
        private String clientId;
        private String sessionId;
        private String ipAddress;
        private String userAgent;
        private Map<String, Object> payload;
    }
}
