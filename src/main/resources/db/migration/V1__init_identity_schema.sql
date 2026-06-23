CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION identity_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;



CREATE TABLE identity_users (
    id UUID PRIMARY KEY,
    email CITEXT NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    password_algorithm VARCHAR(50) NOT NULL DEFAULT 'bcrypt',
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(100),
    avatar_url VARCHAR(512),
    phone VARCHAR(50),
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    locale VARCHAR(20),
    timezone VARCHAR(50),
    deleted_at TIMESTAMP WITH TIME ZONE,
    user_type VARCHAR(50) NOT NULL DEFAULT 'PUBLIC_USER',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_login TIMESTAMP WITH TIME ZONE,
    password_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_user_status CHECK (status IN ('ACTIVE', 'PENDING_VERIFICATION', 'DISABLED', 'LOCKED', 'DELETED')),
    CONSTRAINT valid_user_type CHECK (user_type IN ('PUBLIC_USER', 'COMPANY_USER', 'ADMIN', 'SERVICE_ACCOUNT'))
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
CREATE INDEX idx_login_attempts_email_time ON identity_login_attempts(email, created_at);
CREATE INDEX idx_login_attempts_ip_time ON identity_login_attempts(ip_address, created_at);

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
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    client_secret_hash VARCHAR(255),
    allowed_redirect_urls TEXT,
    allowed_web_origins TEXT,
    token_endpoint_auth_method VARCHAR(50) DEFAULT 'none',
    access_token_ttl_seconds INTEGER,
    refresh_token_ttl_seconds INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_client_type CHECK (client_type IN ('PUBLIC', 'CONFIDENTIAL', 'SERVICE')),
    CONSTRAINT valid_client_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'))
);

CREATE TABLE identity_client_scopes (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES identity_clients(id) ON DELETE CASCADE,
    scope VARCHAR(100) NOT NULL,
    UNIQUE(client_id, scope)
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
CREATE INDEX idx_sessions_user_status ON identity_sessions(user_id, status);
CREATE INDEX idx_sessions_client_status ON identity_sessions(client_id, status);
CREATE INDEX idx_sessions_family_id ON identity_sessions(refresh_token_family_id);

CREATE TABLE identity_refresh_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES identity_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES identity_clients(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    rotated_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by VARCHAR(255),
    revoke_reason VARCHAR(255),
    CONSTRAINT valid_token_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED', 'REUSED'))
);
CREATE INDEX idx_refresh_tokens_family_id ON identity_refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expiry ON identity_refresh_tokens(expires_at);

CREATE TABLE identity_email_verification_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT valid_evt_status CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'REVOKED'))
);

CREATE TABLE identity_password_reset_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT valid_prt_status CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'REVOKED'))
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
CREATE INDEX idx_audit_actor_time ON identity_audit_events(actor_id, created_at);
CREATE INDEX idx_audit_target_time ON identity_audit_events(target_id, created_at);
CREATE INDEX idx_audit_type_time ON identity_audit_events(event_type, created_at);

CREATE TABLE identity_event_outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID,
    event_type VARCHAR(100) NOT NULL,
    event_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    payload TEXT,
    headers TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    CONSTRAINT valid_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD'))
);
CREATE INDEX idx_outbox_status_retry ON identity_event_outbox(status, next_retry_at);

CREATE TABLE identity_key_pairs (
    id UUID PRIMARY KEY,
    kid VARCHAR(100) NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'RS256',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT valid_key_status CHECK (status IN ('ACTIVE', 'RETIRED', 'REVOKED'))
);

CREATE TABLE identity_organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_org_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'))
);

CREATE TABLE identity_organization_members (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES identity_organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(organization_id, user_id),
    CONSTRAINT valid_member_status CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED', 'REMOVED')),
    CONSTRAINT valid_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);
CREATE INDEX idx_org_members_user ON identity_organization_members(user_id, status);
CREATE INDEX idx_org_members_org ON identity_organization_members(organization_id, status);

CREATE TABLE identity_provider_configs (
    id UUID PRIMARY KEY,
    provider_key VARCHAR(50) NOT NULL UNIQUE,
    provider_name VARCHAR(100) NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    issuer_uri TEXT,
    authorization_uri TEXT,
    token_uri TEXT,
    user_info_uri TEXT,
    jwks_uri TEXT,
    scopes TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity_provider_links (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    provider_id UUID NOT NULL REFERENCES identity_provider_configs(id) ON DELETE CASCADE,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email CITEXT,
    provider_username VARCHAR(255),
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(provider_id, provider_user_id),
    UNIQUE(provider_id, user_id)
);
CREATE INDEX idx_provider_links ON identity_provider_links(provider_id, provider_user_id);

-- Insert default safe seed data
INSERT INTO identity_roles (id, name, description) VALUES (gen_random_uuid(), 'ROLE_USER', 'Standard User');
INSERT INTO identity_roles (id, name, description) VALUES (gen_random_uuid(), 'ROLE_ADMIN', 'Administrator');
INSERT INTO identity_roles (id, name, description) VALUES (gen_random_uuid(), 'ROLE_SERVICE', 'Service Account');

INSERT INTO identity_clients (id, client_id, client_name, client_type, status, token_endpoint_auth_method)
VALUES (gen_random_uuid(), 'dev-client', 'Development Client', 'PUBLIC', 'ACTIVE', 'none');

CREATE TRIGGER set_identity_users_updated_at BEFORE UPDATE ON identity_users FOR EACH ROW EXECUTE FUNCTION identity_set_updated_at();
CREATE TRIGGER set_identity_clients_updated_at BEFORE UPDATE ON identity_clients FOR EACH ROW EXECUTE FUNCTION identity_set_updated_at();
CREATE TRIGGER set_identity_organizations_updated_at BEFORE UPDATE ON identity_organizations FOR EACH ROW EXECUTE FUNCTION identity_set_updated_at();
CREATE TRIGGER set_identity_provider_configs_updated_at BEFORE UPDATE ON identity_provider_configs FOR EACH ROW EXECUTE FUNCTION identity_set_updated_at();
