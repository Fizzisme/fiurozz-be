# Project files contract — project-service ↔ ai-service

Implemented service-side contract, based on the supplied M0.4 / ADR-0003 draft
and its 2026-09-27 follow-up answers. This module stores code, not project media.
It does not implement AI runs, SSE, chat, previews or frontend integration.

## Routes and authentication

Gateway prefix: `/api/projects`. The paths below are service-relative.
All eight endpoints require authentication. Gateway-facing requests send a bearer
token; the gateway verifies it and strips it. Internal AI calls propagate the
gateway-verified `X-User-ID` directly to project-service.

IMPORTANT: identity headers are trusted, not cryptographically verified here.
Restrict project-service to the gateway/internal service network. Never let an
external client call it directly with an arbitrary identity header.
`source.kind=AGENT` is history metadata, not proof of a service identity.
The AI tool set must not expose catalog publish/lifecycle operations.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/{projectId}/files?include=content&revision=12` | Manifest; optionally text contents or a historical snapshot |
| POST | `/{projectId}/files/changes` | One atomic new revision; changed paths |
| GET | `/{projectId}/files/revisions?page=0&size=20` | Newest first; `items,page,size,totalElements,totalPages` |
| POST | `/{projectId}/files/revert` | A new revision copying the target tree |
| POST | `/{projectId}/lease` | 201: lease secret and durable `baseRevision` |
| PUT | `/{projectId}/lease/{leaseId}` | 200: renewed lease |
| DELETE | `/{projectId}/lease/{leaseId}` | 204, including unknown/expired IDs |
| GET | `/{projectId}/lease` | Lease status without the secret, or `data:null` |

Responses use the existing `ApiResponse<T>` envelope. Files GET, changes and
revert return a quoted numeric ETag. Mutations require `If-Match: "12"`, using
the **files revision**, never the catalog metadata ETag. Missing/malformed
If-Match is 400. Stale revisions are 412 `FILES_STALE_REVISION`, with the current ETag.
While leased, a write without the matching `Lease-Id` is 423 `PROJECT_LEASED`;
its response data contains lease status but never its secret.

Only the owner may write files or inspect history/leases. Authenticated non-owners
may read only the pinned revision of a PUBLISHED, PUBLIC or UNLISTED project with
`source_visibility=PUBLIC`. Other working/historical revisions are not exposed.
PRIVATE, hidden-source, deleted and unpinned projects return 404 to those readers.
Anonymous reads are intentionally not enabled by this contract.

The existing catalog APIs remain metadata-only. This module does not add an
endpoint to enable public source visibility; existing source privacy defaults stay
unchanged. Publishing alone never changes source visibility.

## Payloads

Apply a batch:

```json
{
  "changes": [
    { "op": "PUT", "path": "app/page.tsx", "content": "export default function Home() {}" },
    { "op": "DELETE", "path": "app/old/page.tsx" }
  ],
  "label": "run:run_abc step 4",
  "source": { "kind": "AGENT", "runId": "run_abc" }
}
```

USER writes use `{"kind":"USER"}` without runId. Duplicate paths in a batch are
rejected rather than silently reordered. DELETE of a missing file is 404.
All validation completes before blob upload. Database state commits all-or-nothing.
Changed surviving paths return path/sha256/size; deleted paths add
`deleted:true`, omit sha256 and have size 0. Content is omitted from manifest responses.

Revert: `{"toRevision":12,"label":"Undo run run_abc"}`.

Acquire: `{"holder":"ai-service","runId":"run_abc","ttlSeconds":120}`.

The acquire/renew response data is:

```json
{
  "leaseId": "93bcd82a-9a16-468e-a87f-0785a56bf738",
  "holder": "ai-service",
  "runId": "run_abc",
  "expiresAt": "2026-10-05T10:02:00Z",
  "baseRevision": 12
}
```

Renew at about TTL/3. Renewal sets expiry to server-now + the original TTL.
TTL defaults to 120 seconds and must be 30..3600. Acquire while actively leased
returns 423, including for the same run; use PUT to renew. A released/expired lease
can be acquired again. The first acquire for a runId permanently fixes its
startRevision. Renew/release require project ownership; an unrelated lease ID
cannot release a successor's lock.

## Storage, revisions and expiry

- PostgreSQL/JPA owns workspace state, immutable complete JSONB manifests and
  durable run checkpoints. The new tables are `project_file_workspaces`,
  `project_file_revisions` and `project_file_run_checkpoints`.
- A new project reads as empty revision 0. Revision 0 is materialized on first use;
  an unused project's history list can be empty. Each successful batch/revert
  advances the files revision exactly once, even if the final bytes are unchanged.
- File operations do not increment `projects.row_version`. Database row locks
  serialize initialization, writes, lease transitions and catalog lifecycle changes.
- MinIO/S3 stores private UTF-8 blobs under `blobs/sha256/{hash}`, in a **separate
  private bucket**. The server computes SHA-256. Identical bytes share one object.
- Blob uploads happen before the manifest commits. A failed transaction can leave
  an unreferenced object, but cannot expose a partial file tree.
- Expiry is enforced on lease/write operations and swept every 30 seconds.
  Run endRevision is recorded before a successor can write. Soft-deleted projects'
  expired runs can also be finalized without reopening access to their source.
- Revert creates a new snapshot; it never deletes or renumbers history.
- All revisions/checkpoints are retained for now. No unspecified N-day pruning is
  enabled, so historical session references and undo points remain valid.
- GC runs at 03:00 UTC. It only removes unreferenced blobs older than 24 hours.
  Writers and GC take the same PostgreSQL per-hash transaction locks, preventing
  deletion during reuse/commit. All retained snapshots protect their blobs.

Limits: 512 KiB/file, 1000 files/tree, 10 MiB/tree, 200 changes/request,
300 characters/path and 200 characters/label. Paths are case-sensitive relative
POSIX paths with no leading slash, backslash, empty, dot or parent segments.
Next.js `[slug]` and `(group)` segments are supported. File/directory collisions
and invalid UTF-8/NUL binary content are rejected. Archived/deleted projects cannot
be edited (deleted projects are hidden with 404).

## Publication

Catalog publication emits a synchronous public catalog event. The files module
pins its current revision **in the same transaction**. A pin failure rolls back
publication. An explicit owner republish of an already PUBLISHED project refreshes
the pin without changing the existing idempotent catalog metadata behavior.
Later file edits or reverts never move the publication pin automatically.

Existing PUBLISHED projects get no implicit pin during migration. They expose no
source until the owner republishes. A pin also does not override source privacy.
No AI workspace operation publishes or changes project visibility.

## Configuration

Set the existing `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`.
Additional settings:

| Variable | Default |
| --- | --- |
| FILES_S3_BUCKET | project-files |
| FILES_S3_AUTO_CREATE_BUCKET | true |
| FILES_GC_ENABLED | true |
| FILES_GC_GRACE_HOURS | 24 |

Do not reuse the public media bucket. This adapter never creates a public policy.
In production provision a private bucket and turn auto-create off if the account
does not have bucket-management privileges. GC will refuse a grace period below
24 hours. Lease sweep and GC cron are configurable Spring properties:
`storage.files.lease-sweep-ms` and `storage.files.gc-cron`.

AI calls are direct on the internal network. Gateway route timeouts remain owned
by the gateway team; its current 5-second project timeout may need tuning for
large user-facing batches. This implementation does not silently change gateway policy.

## Errors

Stable new codes: FILE_PATH_INVALID (400), FILE_NOT_TEXT (400), FILE_NOT_FOUND
(404), REVISION_NOT_FOUND (404), LEASE_NOT_FOUND (404), FILES_STALE_REVISION (412),
FILES_LIMIT_EXCEEDED (413), PROJECT_LEASED (423), FILES_STORAGE_UNAVAILABLE (503).
Existing authentication/project/validation codes remain in use.

## Verification

Verified locally on 2026-10-05: all 121 project-service tests passed, with zero
failures/errors/skips, including 17 files-policy tests and 18 real HTTP/PostgreSQL/
MinIO tests. The suite used disposable databases, not the configured development DB.
Existing Swagger path-count assertions were updated for the six new paths; an
existing owner-snapshot test's incorrect SQL column was corrected to row_version.

With Java 21 and Docker Desktop running:

```powershell
mvn "-Dtest=FilePolicyTest,ProjectFilesHttpIntegrationTest" test
```

The integration test starts isolated PostgreSQL and private MinIO containers,
starts the application on a random HTTP port and sends real HTTP requests.
It verifies atomic saves, history/revert, privacy, stale ETags, concurrent saves,
concurrent leases, renewal/release/expiry, durable checkpoints, publication
pinning and rollback, storage failure rollback, private bucket access, and GC
grace/retention/coordination. It does not call a real AI-service (not in this repo).

The existing full project-service suite also needs its baseline DATABASE_URL;
its older integration tests are not Testcontainers-based.
