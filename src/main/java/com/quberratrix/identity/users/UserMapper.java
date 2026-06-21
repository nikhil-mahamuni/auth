package com.quberratrix.identity.users;

public class UserMapper {
    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getUserType(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getLastLogin(),
                user.getCreatedAt()
        );
    }
}
