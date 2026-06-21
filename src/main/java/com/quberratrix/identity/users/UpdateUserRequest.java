package com.quberratrix.identity.users;

public record UpdateUserRequest(
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl
) {}
