package com.quberratrix.identity.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quberratrix.identity.events.IdentityEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final IdentityEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public Mono<Void> logAndPublishEvent(String eventType, UUID actorId, UUID targetId, UUID clientId, UUID sessionId, String ipAddress, String userAgent, String correlationId, String requestId, Map<String, Object> payload) {
        String metadata;
        try {
            metadata = payload != null ? objectMapper.writeValueAsString(payload) : "{}";
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit metadata for event {}", eventType);
            metadata = "{}";
        }

        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setActorId(actorId);
        event.setTargetId(targetId);
        event.setClientId(clientId);
        event.setSessionId(sessionId);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        event.setCorrelationId(correlationId);
        event.setRequestId(requestId);
        event.setMetadata(metadata);
        event.setCreatedAt(Instant.now());

        return auditEventRepository.save(event)
                .then(eventPublisher.publishEvent(eventType, actorId, targetId, clientId, sessionId, ipAddress, userAgent, correlationId, requestId, payload));
    }
}
