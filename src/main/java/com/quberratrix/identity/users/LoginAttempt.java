package com.quberratrix.identity.users;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_login_attempts")
public class LoginAttempt extends PersistableEntity {
    @Id
    private UUID id;
    private UUID userId;
    private String email;
    private UUID clientId;
    private String ipAddress;
    private String userAgent;
    private boolean success;
    private String failureReason;
    private String correlationId;
    private String requestId;
    private Instant createdAt;
}
