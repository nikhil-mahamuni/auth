package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("roles")
public class Role {
    @Id
    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;
}
