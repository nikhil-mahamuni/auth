package com.quberratrix.identity.events;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class IdentityEventEnvelope {
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
