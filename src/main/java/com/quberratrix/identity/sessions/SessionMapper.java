package com.quberratrix.identity.sessions;

public class SessionMapper {
    public static SessionResponse toResponse(Session session) {
        if (session == null) return null;
        return new SessionResponse(
                session.getId(),
                session.getUserId(),
                session.getClientId(),
                session.getIpAddress(),
                session.getUserAgent(),
                session.getDeviceId(),
                session.getDeviceName(),
                session.getDeviceType(),
                session.getLocation(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getLastUsedAt()
        );
    }
}
