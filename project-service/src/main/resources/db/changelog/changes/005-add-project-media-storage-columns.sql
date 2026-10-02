--liquibase formatted sql

--changeset philia:005-add-project-media-storage-columns dbms:postgresql
ALTER TABLE project_media ADD COLUMN object_key VARCHAR(1024);
ALTER TABLE project_media ADD COLUMN content_type VARCHAR(100);
ALTER TABLE project_media ADD COLUMN size_bytes BIGINT;
ALTER TABLE project_media ADD CONSTRAINT uq_project_media_object_key UNIQUE (object_key);
ALTER TABLE project_media ADD CONSTRAINT ck_project_media_size_bytes
    CHECK (size_bytes IS NULL OR size_bytes > 0);

--rollback ALTER TABLE project_media DROP CONSTRAINT ck_project_media_size_bytes;
--rollback ALTER TABLE project_media DROP CONSTRAINT uq_project_media_object_key;
--rollback ALTER TABLE project_media DROP COLUMN size_bytes;
--rollback ALTER TABLE project_media DROP COLUMN content_type;
--rollback ALTER TABLE project_media DROP COLUMN object_key;
