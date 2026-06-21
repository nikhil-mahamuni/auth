CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE identity_users (
    id UUID PRIMARY KEY,
    email CITEXT NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(100),
    avatar_url VARCHAR(512),
    user_type VARCHAR(50) NOT NULL DEFAULT 'PUBLIC_USER',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_login TIMESTAMP WITH TIME ZONE,
    password_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT valid_user_type CHECK (user_type IN ('PUBLIC_USER', 'ADMIN'))
);
CREATE INDEX idx_users_email ON identity_users(email);
CREATE INDEX idx_users_status ON identity_users(status);

CREATE TABLE identity_login_attempts (
    id UUID PRIMARY KEY,
    user_id UUID,
    email CITEXT,
    client_id UUID,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    correlation_id VARCHAR(100),
    request_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_login_attempts_email ON identity_login_attempts(email);

CREATE TABLE identity_roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity_user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES identity_roles(id) ON DELETE CASCADE,
    UNIQUE(user_id, role_id)
);

CREATE TABLE identity_clients (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_name VARCHAR(100) NOT NULL,
    client_type VARCHAR(50) NOT NULL DEFAULT 'PUBLIC',
    client_secret_hash VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    allowed_redirect_urls TEXT,
    allowed_web_origins TEXT,
    token_endpoint_auth_method VARCHAR(50) DEFAULT 'none',
    access_token_ttl_seconds INTEGER,
    refresh_token_ttl_seconds INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_client_type CHECK (client_type IN ('PUBLIC', 'CONFIDENTIAL', 'SERVICE'))
);

CREATE TABLE identity_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES identity_clients(id) ON DELETE CASCADE,
    refresh_token_family_id UUID,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    device_id VARCHAR(255),
    device_name VARCHAR(255),
    device_type VARCHAR(100),
    location VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT valid_session_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);
CREATE INDEX idx_sessions_user_id ON identity_sessions(user_id);
CREATE INDEX idx_sessions_family_id ON identity_sessions(refresh_token_family_id);

CREATE TABLE identity_refresh_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES identity_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES identity_clients(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    rotated_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by VARCHAR(255),
    revoke_reason VARCHAR(255),
    CONSTRAINT valid_token_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'REUSED'))
);
CREATE INDEX idx_refresh_tokens_family_id ON identity_refresh_tokens(family_id);

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
CREATE INDEX idx_revoked_access_tokens_jti ON identity_revoked_access_tokens(jti);

CREATE TABLE identity_audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    actor_id UUID,
    target_id UUID,
    client_id UUID,
    session_id UUID,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    correlation_id VARCHAR(100),
    request_id VARCHAR(100),
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity_providers (
    id UUID PRIMARY KEY,
    provider_id VARCHAR(50) NOT NULL UNIQUE,
    provider_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity_user_providers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    provider_id UUID NOT NULL REFERENCES identity_providers(id) ON DELETE CASCADE,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider_id, provider_user_id)
);

CREATE TABLE identity_key_pairs (
    id UUID PRIMARY KEY,
    kid VARCHAR(100) NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    private_key TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT valid_key_status CHECK (status IN ('ACTIVE', 'RETIRED', 'REVOKED'))
);

-- Insert default roles
INSERT INTO identity_roles (id, name, description) VALUES (gen_random_uuid(), 'ROLE_USER', 'Standard User');
INSERT INTO identity_roles (id, name, description) VALUES (gen_random_uuid(), 'ROLE_ADMIN', 'Administrator');
INSERT INTO identity_roles (id, name, description) VALUES (gen_random_uuid(), 'ROLE_SERVICE', 'Service Account');

-- Insert default client for local dev
INSERT INTO identity_clients (id, client_id, client_name, client_type, enabled, token_endpoint_auth_method)
VALUES (gen_random_uuid(), 'dev-client', 'Development Client', 'PUBLIC', true, 'none');

INSERT INTO identity_clients (id, client_id, client_name, client_type, client_secret_hash, enabled, token_endpoint_auth_method)
VALUES (gen_random_uuid(), 'service-client', 'Service Client', 'SERVICE', '$2a$12$R.3e.z82t.n61.B1K8R7ZebE.P9iC6V9l1aX1T2/vP8jR3m9rX52m', true, 'client_secret_post'); -- Secret: service-secret
