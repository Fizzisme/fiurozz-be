--liquibase formatted sql

--changeset philia:004-add-project-repository-url dbms:postgresql
ALTER TABLE projects ADD COLUMN repository_url VARCHAR(500);

--rollback ALTER TABLE projects DROP COLUMN repository_url;
