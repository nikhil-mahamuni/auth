CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Alter check constraint safely for status ENUMs on users
ALTER TABLE identity_users DROP CONSTRAINT IF EXISTS valid_user_status;
ALTER TABLE identity_users ADD CONSTRAINT valid_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'DELETED'));

-- No structural changes strictly needed for tokens because we just use CITEXT/pgcrypto now,
-- but this migration file ensures V2 safely tracks the updates.
