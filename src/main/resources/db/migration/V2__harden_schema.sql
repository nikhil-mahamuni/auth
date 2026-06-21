-- 1. Rename existing tables to identity_ prefix
ALTER TABLE users RENAME TO identity_users;
ALTER TABLE roles RENAME TO identity_roles;
ALTER TABLE user_roles RENAME TO identity_user_roles;
ALTER TABLE clients RENAME TO identity_clients;
ALTER TABLE sessions RENAME TO identity_sessions;
ALTER TABLE refresh_tokens RENAME TO identity_refresh_tokens;
ALTER TABLE user_providers RENAME TO identity_user_providers;

-- 2. Add columns to identity_users
ALTER TABLE identity_users ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE identity_users ADD COLUMN password_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- 3. Add columns to identity_sessions
ALTER TABLE identity_sessions ADD COLUMN device_id VARCHAR(255);
ALTER TABLE identity_sessions ADD COLUMN device_name VARCHAR(255);
ALTER TABLE identity_sessions ADD COLUMN device_type VARCHAR(100);
ALTER TABLE identity_sessions ADD COLUMN location VARCHAR(255);
ALTER TABLE identity_sessions ADD COLUMN refresh_token_family_id UUID;
ALTER TABLE identity_sessions ADD COLUMN revoked_at TIMESTAMP WITH TIME ZONE;

-- 4. Add columns to identity_refresh_tokens for rotation/family
ALTER TABLE identity_refresh_tokens ADD COLUMN family_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE identity_refresh_tokens ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE identity_refresh_tokens ADD COLUMN last_used_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE identity_refresh_tokens ADD COLUMN rotated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE identity_refresh_tokens ADD COLUMN revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE identity_refresh_tokens ADD COLUMN revoked_by VARCHAR(255);
ALTER TABLE identity_refresh_tokens ADD COLUMN revoke_reason VARCHAR(255);

-- 5. Add new tables

CREATE TABLE identity_email_verification_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE identity_password_reset_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE identity_revoked_access_tokens (
    id UUID PRIMARY KEY,
    jti UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE identity_audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    actor_id UUID,
    target_id UUID,
    client_id UUID,
    session_id UUID,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
