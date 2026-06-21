package com.quberratrix.identity.events;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_event_outbox")
public class EventOutbox extends PersistableEntity {
    @Id
    private UUID id;
    private String aggregateType;
    private UUID aggregateId;
    private String eventType;
    private String eventVersion;
    private String payload;
    private String headers;
    private String status;
    private int retryCount;
    private Instant nextRetryAt;
    private Instant createdAt;
    private Instant publishedAt;
    private String errorMessage;
}
