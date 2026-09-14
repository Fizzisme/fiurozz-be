# Project Service

## Architecture guides

- [Domain-Driven Design for Project Service](docs/architecture/01-domain-driven-design.md)
- [Clean Architecture for Project Service](docs/architecture/02-clean-architecture.md)
- [Project Service architecture blueprint](docs/architecture/03-project-service-architecture-blueprint.md)

## API design

- [Phase 1 Catalog Management API](docs/catalog-management-api/README.md)

## PostgreSQL database

The service connects to PostgreSQL through a single `DATABASE_URL` JDBC
connection string (same convention as `auth-service` and `user-service`), so
switching hosts — e.g. from a Neon dev branch to a production database — is a
one-variable change, not a code change. There is no local Postgres container;
copy `.env.example` to `.env` and fill in your own connection string:

```
DATABASE_URL=jdbc:postgresql://<host>/<database>?user=<user>&password=<password>&sslmode=require
```

`.env` is gitignored — never commit real credentials.

To start the service and shared infrastructure (Redis) from the repository
root:

```powershell
docker compose up -d
```

Run the service or its tests directly:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
```

## Liquibase migrations

Spring Boot automatically applies
`src/main/resources/db/changelog/db.changelog-master.yaml` at startup. The
current changelogs create the project schema and its indexes.

Never edit a changeset after it has been applied to a shared database. Add a new
numbered changelog under `db/changelog/changes/` and include it at the end of the
master changelog instead.
