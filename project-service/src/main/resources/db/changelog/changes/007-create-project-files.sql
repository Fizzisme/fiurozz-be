--liquibase formatted sql

--changeset philia:007-create-project-files dbms:postgresql
-- Separate workspace version: metadata updates do not increment files_revision.
CREATE TABLE project_file_workspaces (
    project_id UUID PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    published_revision BIGINT CHECK (published_revision >= 0 AND published_revision <= revision),
    lease_id UUID,
    lease_owner_id UUID,
    holder VARCHAR(100),
    run_id VARCHAR(200),
    ttl_seconds INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ,
    base_revision BIGINT NOT NULL DEFAULT 0 CHECK (base_revision >= 0),
    CONSTRAINT ck_project_file_lease CHECK (
        (lease_id IS NULL AND lease_owner_id IS NULL AND holder IS NULL AND run_id IS NULL
            AND expires_at IS NULL AND ttl_seconds = 0)
        OR (lease_id IS NOT NULL AND lease_owner_id IS NOT NULL AND holder IS NOT NULL AND run_id IS NOT NULL
            AND expires_at IS NOT NULL AND ttl_seconds BETWEEN 30 AND 3600)
    )
);

CREATE TABLE project_file_revisions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    manifest JSONB NOT NULL CHECK (jsonb_typeof(manifest) = 'object'),
    label VARCHAR(200),
    source_kind VARCHAR(10) NOT NULL CHECK (source_kind IN ('USER', 'AGENT')),
    run_id VARCHAR(200),
    changed_paths INTEGER NOT NULL CHECK (changed_paths >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_project_file_revision UNIQUE(project_id, revision),
    CONSTRAINT ck_project_file_revision_source CHECK (
        (source_kind = 'AGENT' AND run_id IS NOT NULL) OR (source_kind = 'USER' AND run_id IS NULL)
    )
);

CREATE TABLE project_file_run_checkpoints (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    run_id VARCHAR(200) NOT NULL,
    start_revision BIGINT NOT NULL CHECK (start_revision >= 0),
    end_revision BIGINT CHECK (end_revision >= start_revision),
    CONSTRAINT uq_project_file_run UNIQUE(project_id, run_id)
);

CREATE INDEX idx_project_file_lease_expiration ON project_file_workspaces(expires_at) WHERE lease_id IS NOT NULL;
-- Existing published projects intentionally have no publication pin until the owner republishes.
INSERT INTO project_file_workspaces(project_id) SELECT id FROM projects;
--rollback DROP TABLE project_file_run_checkpoints;
--rollback DROP TABLE project_file_revisions;
--rollback DROP TABLE project_file_workspaces;
