-- 1. Create Event Outbox table
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
    CONSTRAINT valid_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTER'))
);
CREATE INDEX idx_outbox_status ON identity_event_outbox(status);

-- 2. External Provider Foundation Support
CREATE TABLE identity_provider_config (
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

CREATE TABLE identity_user_provider_links (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity_users(id) ON DELETE CASCADE,
    provider_id UUID NOT NULL REFERENCES identity_provider_config(id) ON DELETE CASCADE,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email CITEXT,
    provider_username VARCHAR(255),
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(provider_id, provider_user_id),
    UNIQUE(provider_id, user_id)
);
