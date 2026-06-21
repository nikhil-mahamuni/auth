package com.quberratrix.identity.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class IdentityEventPublisher {

    private final ObjectMapper objectMapper;
    private final EventOutboxRepository outboxRepository;

    @Value("${spring.application.name}")
    private String applicationName;

    public IdentityEventPublisher(ObjectMapper objectMapper, EventOutboxRepository outboxRepository) {
        this.objectMapper = objectMapper;
        this.outboxRepository = outboxRepository;
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
                    EventOutbox outbox = new EventOutbox();
                    outbox.setId(UUID.randomUUID());
                    outbox.setAggregateType("identity");
                    outbox.setAggregateId(targetUserId != null ? targetUserId : actorUserId);
                    outbox.setEventType(eventType);
                    outbox.setEventVersion("v1");
                    outbox.setPayload(json);
                    outbox.setHeaders("{}"); // Minimal headers
                    outbox.setStatus("PENDING");
                    outbox.setRetryCount(0);
                    outbox.setCreatedAt(Instant.now());

                    return outboxRepository.save(outbox);
                })
                .onErrorResume(e -> {
                    log.error("Serialization or Outbox insert failed for event: " + eventType, e);
                    return Mono.empty();
                })
                .then();
    }
}
