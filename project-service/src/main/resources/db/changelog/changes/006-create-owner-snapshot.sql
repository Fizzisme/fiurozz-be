--liquibase formatted sql

--changeset philia:006-create-owner-snapshot dbms:postgresql
-- Local copy of the owner fields owned by user-service, kept up to date from user.events.
-- display_name is nullable so an avatar or name event that arrives before account.created is
-- not lost; Create Project still requires a display name.
CREATE TABLE owner_snapshot (
    user_id UUID PRIMARY KEY,
    display_name VARCHAR(120),
    avatar_url VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--rollback DROP TABLE owner_snapshot;
