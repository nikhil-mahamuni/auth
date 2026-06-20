package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("users")
public class User {
    @Id
    private UUID id;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String displayName;
    private String avatarUrl;
    private String userType;
    private String status;
    private boolean emailVerified;
    private int loginAttempts;
    private Instant lastLogin;
    private Instant createdAt;
    private Instant updatedAt;
}
