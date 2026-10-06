# ADR — Project files workspace module

Status: implemented service-side, 2026-10-05.

## Context

The catalog owns project metadata and lifecycle. The existing source module is
planned for GitHub/repository snapshots; release/project_versions is for named
releases. Neither is the mutable AI editing workspace or its per-save history.
The supplied ADR-0003 requests that project-service remain the source of truth
for frontend and AI workspace files.

## Decision

Add a files business module inside the existing Spring Boot application/database.
Follow the existing api → internal/application/domain/adapter structure. Do not
add another deployable service, AI engine, shared database or frontend feature.

Allowed dependencies:

```text
files → catalog::api
files → shared security/web infrastructure
catalog emits ProjectPublishedEvent in catalog::api
files consumes that event synchronously
```

Catalog never imports files internals. ProjectFilesAccess is the public boundary
for authorization/lifecycle state and row locks. Files owns its own JPA entities,
repositories, manifests, checkpoints, blob adapter and maintenance jobs.

Each snapshot contains the complete path → SHA-256/byte-size manifest in JSONB.
Content lives in a separate private MinIO/S3 bucket. This uses PostgreSQL for
atomic manifest/revision transitions without pretending S3 has multi-object
transactions. Old snapshots/checkpoints are retained; no N-day pruning policy is
invented before the team chooses one.

Serialize workspace mutations on the catalog project row, including first-use
initialization and lifecycle/publication races. Do not change projects.row_version
for file saves. Sorted PostgreSQL transaction-scoped per-hash advisory locks
coordinate writers with GC across application instances. GC only deletes old,
unreferenced objects after acquiring the same lock; reuse cannot race deletion.

The files publication pointer is updated by a synchronous catalog publication
event in the same database transaction. Existing published projects are not
implicitly backfilled. An explicit owner republish may move the files pointer;
background agent saves never do. Source visibility remains a separate privacy gate.

## Consequences

- One service, one Liquibase history, no dependency cycle between business modules.
- PostgreSQL-specific locks and JSONB queries stay inside the JPA output adapter.
- S3 uploads may become orphans on rollback; grace-period GC handles them safely.
- Full immutable manifests cost more metadata than delta storage, but simplify
  reads/revert and fit the initial 1000-file/10-MiB workspace limits.
- Identity headers retain the existing internal-network trust model. Deployment
  must enforce that boundary; source.kind is not a service authorization claim.
- Runtime verification uses real HTTP, PostgreSQL and private MinIO containers.
  Real AI integration remains the AI team's adapter work.

See [the implemented HTTP contract](../project-files-api/README.md) for endpoints,
payloads, limits, errors and test commands.
