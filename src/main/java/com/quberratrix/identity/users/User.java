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
@Table("identity_users")
public class User extends PersistableEntity {
    @Id
    private UUID id;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String displayName;
    private String avatarUrl;
    private String phone;
    private boolean phoneVerified;
    private String locale;
    private String timezone;
    private String passwordAlgorithm;
    private Instant deletedAt;
    private String userType;
    private String status;
    private boolean emailVerified;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLogin;
    private Instant passwordUpdatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
