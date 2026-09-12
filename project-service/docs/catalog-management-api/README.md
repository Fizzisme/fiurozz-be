# Phase 1 Catalog Management API

## Status

This document defines the recommended HTTP API contract for Phase 1 of Product
Service. It covers Project Catalog Management, Project Lifecycle Management,
and Public Project Discovery.

The contract is aligned with:

- [`001-create-project-schema.sql`](../../src/main/resources/db/changelog/changes/001-create-project-schema.sql)
- [`002-create-project-indexes.sql`](../../src/main/resources/db/changelog/changes/002-create-project-indexes.sql)
- [Project Service architecture blueprint](../architecture/03-project-service-architecture-blueprint.md)

Phase 1 uses only the `catalog` module. GitHub integration, source snapshots,
object-storage uploads, versions, likes, comments, and share analytics are not
part of this contract.

The currently implemented OpenAPI document contains endpoints 1 and 2: Create
Project and Get Project by ID. Its Swagger UI is available at
`/swagger-ui.html`, and the generated OpenAPI JSON is available at
`/v3/api-docs`.

## Endpoint summary

Phase 1 contains 15 endpoints:

| # | Method | Endpoint | Authentication | Use case |
|---:|---|---|---|---|
| 1 | `POST` | `/v1/projects` | Required | `CreateProject` |
| 2 | `GET` | `/v1/projects/{projectId}` | Optional | `GetProject` |
| 3 | `PATCH` | `/v1/projects/{projectId}` | Required | `UpdateProject` |
| 4 | `DELETE` | `/v1/projects/{projectId}` | Required | `DeleteProject` |
| 5 | `GET` | `/v1/me/projects` | Required | `ListMyProjects` |
| 6 | `PUT` | `/v1/projects/{projectId}/tags` | Required | `ReplaceProjectTags` |
| 7 | `POST` | `/v1/projects/{projectId}/publish` | Required | `PublishProject` |
| 8 | `POST` | `/v1/projects/{projectId}/archive` | Required | `ArchiveProject` |
| 9 | `POST` | `/v1/projects/{projectId}/reopen` | Required | `ReopenProject` |
| 10 | `PATCH` | `/v1/projects/{projectId}/visibility` | Required | `ChangeProjectVisibility` |
| 11 | `GET` | `/v1/projects` | Public | `SearchPublishedProjects` |
| 12 | `GET` | `/v1/owners/{ownerId}/projects/{slug}` | Public | `GetPublishedProjectBySlug` |
| 13 | `GET` | `/v1/categories` | Public | `ListProjectCategories` |
| 14 | `GET` | `/v1/categories/{categoryId}/subcategories` | Public | `ListProjectSubCategories` |
| 15 | `GET` | `/v1/tags` | Public | `SearchProjectTags` |

## Common conventions

### Base path and content types

```text
Service path: /v1
Gateway path: /api/project/v1
Request:      application/json
Response:     application/json
Error:        application/json
Identifier:   UUID
Timestamp:    ISO-8601 UTC
```

Every JSON response is wrapped in `ApiResponse<T>`:

```json
{
  "success": true,
  "code": "PROJECT_CREATED",
  "message": "Project created successfully.",
  "data": {},
  "errors": {},
  "timestamp": "2026-07-24T09:00:00Z"
}
```

`data` contains the endpoint-specific DTO. For failed requests, `data` is
`null` and `errors` contains field-level validation messages when applicable.

### Authentication and ownership

Authenticated requests use:

```http
Authorization: Bearer <access-token>
```

The server obtains these values from a trusted authenticated actor or User
Service lookup:

```text
ownerId
ownerDisplayName
ownerAvatarUrl
```

They must never be trusted from a Project request body. The current database
requires both `owner_id` and `owner_display_name`, so the authentication
contract must supply both values before `CreateProject` can be implemented.

### Optimistic concurrency

The `projects.row_version` column protects concurrent writes. Project detail
responses return an ETag:

```http
ETag: "4"
```

Every mutation of an existing Project requires the current value:

```http
If-Match: "4"
```

If another request has already changed the Project, return:

```http
HTTP/1.1 412 Precondition Failed
```

The successful mutation returns the new ETag.

### Pagination

Collection endpoints use zero-based pagination:

```text
page=0
size=20
```

Recommended limits:

```text
Default size: 20
Maximum size: 50
```

Standard collection response:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### Error response

Use the same `ApiResponse` envelope with a stable application code:

```json
{
  "success": false,
  "code": "PROJECT_SLUG_CONFLICT",
  "message": "You already have an active project using this slug.",
  "data": null,
  "errors": {},
  "timestamp": "2026-07-24T09:00:00Z"
}
```

Recommended error codes:

| Code | HTTP status | Meaning |
|---|---:|---|
| `VALIDATION_FAILED` | 400 | Request fields are invalid. |
| `AUTHENTICATION_REQUIRED` | 401 | A valid authenticated actor is required. |
| `PROJECT_FORBIDDEN` | 403 | The actor may see the resource but cannot perform the command. |
| `PROJECT_NOT_FOUND` | 404 | Project does not exist or is intentionally hidden. |
| `CATEGORY_NOT_FOUND` | 404 | Category does not exist or is deleted. |
| `SUBCATEGORY_NOT_FOUND` | 404 | Subcategory does not exist or is deleted. |
| `TAG_NOT_FOUND` | 404 | At least one supplied Tag does not exist. |
| `PROJECT_SLUG_CONFLICT` | 409 | The owner already has an active Project with the slug. |
| `PROJECT_INVALID_STATE` | 409 | The requested lifecycle transition is not allowed. |
| `SUBCATEGORY_NOT_AVAILABLE` | 409 | The selected subcategory is missing, inactive, or deleted. |
| `TAGS_NOT_AVAILABLE` | 409 | At least one supplied Tag is missing or inactive. |
| `SUBCATEGORY_NOT_ACTIVE` | 409 | The selected subcategory cannot receive Projects. |
| `TAG_NOT_ACTIVE` | 409 | At least one supplied Tag is inactive. |
| `PROJECT_STALE_VERSION` | 412 | `If-Match` does not match `row_version`. |

For a private Project requested by another user, prefer `404` to avoid leaking
that the Project exists.

## Shared Project representations

### CreateProjectRequest

```json
{
  "subCategoryId": "939dbfc5-e00c-40d8-9351-499df2562304",
  "title": "Fiurozz Backend",
  "slug": "fiurozz-backend",
  "shortDescription": "A platform for publishing software projects.",
  "description": "The complete project description.",
  "demoUrl": "https://demo.example.com",
  "visibility": "PRIVATE",
  "techStack": [
    "java",
    "spring-boot",
    "postgresql"
  ],
  "features": [
    "Project catalog",
    "Project discovery"
  ],
  "tagIds": [
    "2ed51a2d-3ca7-4463-8402-c82a12255c92"
  ]
}
```

Field validation:

| Field | Required | Database limit | Application rule |
|---|---:|---:|---|
| `subCategoryId` | Yes | UUID FK | Must reference an active, non-deleted subcategory. |
| `title` | Yes | 180 characters | Trimmed and nonblank. |
| `slug` | Yes | 180 characters | Lowercase normalized slug; unique per active owner. |
| `shortDescription` | Yes | 500 characters | Trimmed and nonblank. |
| `description` | Yes | PostgreSQL `TEXT` | Nonblank; apply a sensible application maximum. |
| `demoUrl` | No | 500 characters | Valid HTTPS URL when present. |
| `visibility` | No | Enum | Defaults to `PRIVATE`. |
| `techStack` | No | JSON array | Defaults to `[]`; unique normalized strings. |
| `features` | No | JSON array | Defaults to `[]`; nonblank strings. |
| `tagIds` | No | UUID collection | Defaults to `[]`; all IDs must reference active Tags. |

Recommended JSON-array limits:

```text
techStack: maximum 20 unique items, 60 characters per item
features:  maximum 30 unique items, 200 characters per item
tags:      maximum 10 unique IDs
```

### UpdateProjectRequest

Every field is optional, but at least one field must be present:

```json
{
  "subCategoryId": "939dbfc5-e00c-40d8-9351-499df2562304",
  "title": "Updated Fiurozz Backend",
  "slug": "updated-fiurozz-backend",
  "shortDescription": "An updated short description.",
  "description": "An updated complete description.",
  "demoUrl": "https://new-demo.example.com",
  "techStack": [
    "java",
    "spring-boot",
    "postgresql",
    "redis"
  ],
  "features": [
    "Project catalog",
    "Project publication"
  ]
}
```

The general update request cannot modify:

```text
owner
status
visibility
sourceVisibility
tags
counters
publishedAt
deletedAt
rowVersion
```

Lifecycle, visibility, and tag changes use dedicated commands.

### ProjectDetailResponse

```json
{
  "id": "ff82810c-bb24-46cf-b25f-48cb96532cda",
  "owner": {
    "id": "8e9bf6be-2030-4347-8793-c65e683365c3",
    "displayName": "Philia",
    "avatarUrl": null
  },
  "category": {
    "id": "6fa8c1ef-2bc6-4c94-b478-20e6167e128c",
    "key": "software-development",
    "slug": "software-development",
    "title": "Software Development",
    "icon": "code"
  },
  "subCategory": {
    "id": "939dbfc5-e00c-40d8-9351-499df2562304",
    "key": "backend-development",
    "slug": "backend-development",
    "title": "Backend Development"
  },
  "title": "Fiurozz Backend",
  "slug": "fiurozz-backend",
  "shortDescription": "A platform for publishing software projects.",
  "description": "The complete project description.",
  "thumbnailUrl": null,
  "demoUrl": "https://demo.example.com",
  "techStack": [
    "java",
    "spring-boot",
    "postgresql"
  ],
  "features": [
    "Project catalog",
    "Project discovery"
  ],
  "tags": [
    {
      "id": "2ed51a2d-3ca7-4463-8402-c82a12255c92",
      "slug": "backend",
      "displayName": "Backend"
    }
  ],
  "status": "PUBLISHED",
  "visibility": "PUBLIC",
  "sourceVisibility": "HIDDEN",
  "statistics": {
    "viewCount": 0,
    "likeCount": 0,
    "commentCount": 0
  },
  "publishedAt": "2026-07-24T09:30:00Z",
  "createdAt": "2026-07-24T09:00:00Z",
  "updatedAt": "2026-07-24T09:30:00Z",
  "version": 2
}
```

The owner representation can include `sourceVisibility`. The Phase 1 public
representation may omit it because source access is not implemented yet.

## 1. Create Project

```http
POST /v1/projects
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request: `CreateProjectRequest`.

Behavior:

1. Resolve owner ID, display name, and avatar from the authenticated actor.
2. Validate and normalize the slug.
3. Validate the active subcategory and derive its parent category.
4. Validate all initial Tag IDs.
5. Reject an active duplicate `(owner_id, slug)`.
6. Create the Project as `DRAFT` with `source_visibility = HIDDEN`.
7. Insert initial `project_tags` in the same transaction.

Success:

```http
HTTP/1.1 201 Created
Location: /v1/projects/ff82810c-bb24-46cf-b25f-48cb96532cda
ETag: "0"
```

Response: `ApiResponse<ProjectDetailResponse>`. `data` has the same fields as
`ProjectDetailResponse`; when `tagIds` are supplied, the resolved Tags must be
present in `data.tags`.

Errors: `400`, `401`, `404`, `409`.

## 2. Get Project by ID

```http
GET /v1/projects/{projectId}
Authorization: Bearer <access-token>  # optional
```

Access rules:

| Project state | Owner | Public caller |
|---|---:|---:|
| `DRAFT + PRIVATE` | Allowed | Hidden |
| `PUBLISHED + PUBLIC` | Allowed | Allowed |
| `PUBLISHED + UNLISTED` | Allowed | Allowed by direct lookup |
| `PUBLISHED + PRIVATE` | Allowed | Hidden |
| `ARCHIVED` | Allowed | Hidden |
| Soft deleted | Hidden | Hidden |

Success: `200 OK`, `ETag`, and `ApiResponse<ProjectDetailResponse>`.

Errors: `400`, `404`.

## 3. Update Project

```http
PATCH /v1/projects/{projectId}
Authorization: Bearer <access-token>
If-Match: "2"
Content-Type: application/json
```

Request: `UpdateProjectRequest`.

Rules:

- Only the owner or an authorized administrator may update the Project.
- An archived Project must be reopened before it can be edited.
- A changed slug must remain unique for the active owner.
- A changed subcategory must be active and non-deleted.
- The command cannot change lifecycle, visibility, tags, or counters.

Success: `200 OK`, updated `ETag`, and `ProjectDetailResponse`.

Errors: `400`, `401`, `403`, `404`, `409`, `412`.

## 4. Soft-delete Project

```http
DELETE /v1/projects/{projectId}
Authorization: Bearer <access-token>
If-Match: "3"
```

Rules:

- Set `deleted_at`; never physically delete the Project from this endpoint.
- `DRAFT` and `ARCHIVED` Projects may be deleted.
- A `PUBLISHED` Project must be archived first.
- Deleted Projects disappear from owner and public queries.
- The partial unique index allows a deleted slug to be reused.

Success:

```http
HTTP/1.1 204 No Content
```

Errors: `401`, `403`, `404`, `409`, `412`.

## 5. List My Projects

```http
GET /v1/me/projects?status=DRAFT&visibility=PRIVATE&q=backend&page=0&size=20&sort=createdAt,desc
Authorization: Bearer <access-token>
```

Supported parameters:

| Parameter | Values |
|---|---|
| `status` | `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| `visibility` | `PUBLIC`, `UNLISTED`, `PRIVATE` |
| `q` | Owner-project title/description query |
| `page` | Zero-based page |
| `size` | `1` to `50` |
| `sort` | `createdAt,desc`, `createdAt,asc`, `updatedAt,desc` |

Always apply:

```text
owner_id = authenticated actor
deleted_at IS NULL
```

Success: `200 OK` with paginated Project summaries.

Errors: `400`, `401`.

## 6. Replace Project Tags

```http
PUT /v1/projects/{projectId}/tags
Authorization: Bearer <access-token>
If-Match: "3"
Content-Type: application/json
```

```json
{
  "tagIds": [
    "b12253a6-b460-4776-b27c-845ee3426c5e",
    "c044f552-45ac-4533-bf92-02233c4b3c50"
  ]
}
```

Rules:

- The list replaces the complete existing Tag assignment.
- Every Tag must exist and be `ACTIVE`.
- Reject or normalize duplicate IDs.
- Limit the assignment to 10 Tags.
- An empty list removes every Tag.
- Replace the relationships and update the Project version in one transaction.

Success: `200 OK`, updated `ETag`, and the resolved Tag collection.

Errors: `400`, `401`, `403`, `404`, `409`, `412`.

## 7. Publish Project

```http
POST /v1/projects/{projectId}/publish
Authorization: Bearer <access-token>
If-Match: "4"
```

Transition:

```text
DRAFT -> PUBLISHED
```

Publication requirements:

- Project is active and owned by the actor.
- Required descriptions are present.
- Slug remains valid.
- Subcategory and Tags remain active.
- Demo URL is valid when present.
- Project is not archived or deleted.

Update `status` and `published_at` atomically. A repeated command for an
already-published Project may return the existing representation.

Success: `200 OK`, updated `ETag`, and `ProjectDetailResponse`.

Errors: `401`, `403`, `404`, `409`, `412`.

## 8. Archive Project

```http
POST /v1/projects/{projectId}/archive
Authorization: Bearer <access-token>
If-Match: "5"
```

Allowed transitions:

```text
DRAFT     -> ARCHIVED
PUBLISHED -> ARCHIVED
```

An archived Project is hidden from public discovery and public detail, remains
visible to its owner, and preserves its data. Repeating the command may return
the existing archived representation.

Success: `200 OK`, updated `ETag`, and `ProjectDetailResponse`.

Errors: `401`, `403`, `404`, `409`, `412`.

## 9. Reopen Project

```http
POST /v1/projects/{projectId}/reopen
Authorization: Bearer <access-token>
If-Match: "6"
```

Transition:

```text
ARCHIVED -> DRAFT
```

Recommended updates:

```text
status = DRAFT
visibility = PRIVATE
published_at = null
```

The owner must explicitly publish the Project again before it returns to public
discovery. `reopen` is intentionally different from restoring a soft-deleted
Project.

Success: `200 OK`, updated `ETag`, and `ProjectDetailResponse`.

Errors: `401`, `403`, `404`, `409`, `412`.

## 10. Change Project Visibility

```http
PATCH /v1/projects/{projectId}/visibility
Authorization: Bearer <access-token>
If-Match: "5"
Content-Type: application/json
```

```json
{
  "visibility": "UNLISTED"
}
```

Visibility semantics:

| Visibility | Public search | Direct public lookup | Owner access |
|---|---:|---:|---:|
| `PUBLIC` | Yes, when published | Yes, when published | Yes |
| `UNLISTED` | No | Yes, when published | Yes |
| `PRIVATE` | No | No | Yes |

`source_visibility` remains `HIDDEN` during Phase 1 and is not accepted by this
endpoint.

Success: `200 OK`, updated `ETag`, and `ProjectDetailResponse`.

Errors: `400`, `401`, `403`, `404`, `412`.

## 11. Public Project Search and Discovery

```http
GET /v1/projects?q=spring&categorySlug=developer-tools&subCategorySlug=api-platform&tag=backend&ownerId=<uuid>&cursor=<opaque>&limit=20&sort=newest
```

Supported parameters:

| Parameter | Meaning |
|---|---|
| `q` | Query title and short description. |
| `categoryId` | Include Projects whose subcategory belongs to the Category. |
| `subCategoryId` | Include Projects in one subcategory. |
| `categorySlug` | Slug alternative used by frontend category routes. |
| `subCategorySlug` | Slug alternative used by frontend subcategory routes. |
| `tag` | Include Projects assigned to the Tag slug. |
| `ownerId` | Include public Projects from one owner. |
| `cursor` | Opaque continuation token returned by the preceding response. |
| `limit` | `1` to `50`; defaults to `20`. |
| `sort` | Initially `newest` or `oldest`. |

The server always applies:

```text
status = PUBLISHED
visibility = PUBLIC
deleted_at IS NULL
```

Category and Tag listing are filters on this collection; do not add separate
`/projects/by-category` or `/projects/by-tag` endpoints.

The current indexes support subcategory/status/visibility/date discovery, but
not text search. `ILIKE` is acceptable for an initial small dataset. Add a new
Liquibase full-text or trigram index before relying on text search at scale.

Success: `200 OK` with public Project cards and cursor metadata:

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false
}
```

Cards contain nested Category, Subcategory, and Owner references plus the
technology stack required by the frontend discovery view. The cursor is based
on `(published_at, project_id)` so equal publication timestamps remain stable.

Errors: `400`.

## 12. Get Public Project by Owner and Slug

```http
GET /v1/owners/{ownerId}/projects/{slug}
```

The owner must be present in the path because the database guarantees active
slug uniqueness only for `(owner_id, slug)`, not for `slug` globally.

Access rules:

| State | Result |
|---|---|
| `PUBLISHED + PUBLIC` | `200 OK` |
| `PUBLISHED + UNLISTED` | `200 OK` through direct lookup |
| `PUBLISHED + PRIVATE` | `404 Not Found` |
| `DRAFT` | `404 Not Found` |
| `ARCHIVED` | `404 Not Found` |
| Soft deleted | `404 Not Found` |

Success: `200 OK` with the public Project detail representation. Image media
are sorted by `sort_order`, and `githubUrl` identifies the active primary
repository when one is connected.

Errors: `400`, `404`.

## 13. List Project Categories

```http
GET /v1/categories
```

Always apply:

```text
is_active = true
deleted_at IS NULL
```

Sort by:

```text
sort_order ASC, title ASC
```

Response:

```json
{
  "items": [
    {
      "id": "6fa8c1ef-2bc6-4c94-b478-20e6167e128c",
      "key": "software-development",
      "slug": "software-development",
      "title": "Software Development",
      "icon": "code",
      "sortOrder": 10
    }
  ]
}
```

Success: `200 OK`.

## 14. List Project Subcategories

```http
GET /v1/categories/{categoryId}/subcategories
```

Always apply:

```text
category_id = path category ID
is_active = true
deleted_at IS NULL
```

Sort by:

```text
sort_order ASC, title ASC
```

Response:

```json
{
  "items": [
    {
      "id": "939dbfc5-e00c-40d8-9351-499df2562304",
      "categoryId": "6fa8c1ef-2bc6-4c94-b478-20e6167e128c",
      "key": "backend-development",
      "slug": "backend-development",
      "title": "Backend Development",
      "sortOrder": 10
    }
  ]
}
```

Success: `200 OK`.

Errors: `400`, `404`.

## 15. Search Project Tags

```http
GET /v1/tags?q=spring&page=0&size=20
```

Always apply:

```text
status = ACTIVE
```

Query `display_name`, `normalized_name`, and `slug`. Return only the public Tag
identity; `normalized_name` remains an internal matching detail.

Response:

```json
{
  "items": [
    {
      "id": "2ed51a2d-3ca7-4463-8402-c82a12255c92",
      "slug": "spring-boot",
      "displayName": "Spring Boot"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Success: `200 OK`.

Errors: `400`.

## Lifecycle and visibility model

```text
             publish
    DRAFT ------------> PUBLISHED
      |                     |
      | archive             | archive
      v                     v
                 ARCHIVED
                     |
                     | reopen
                     v
                   DRAFT
```

`status` and `visibility` are separate concepts:

- Status describes the Project lifecycle.
- Visibility describes who can access a published Project.
- Only `PUBLISHED + PUBLIC` appears in discovery.
- `PUBLISHED + UNLISTED` is available only through direct lookup.
- Private, draft, archived, and deleted Projects remain outside public access.

## Schema-owned and deferred fields

The client never controls these fields directly:

```text
id
owner_id
owner_display_name
owner_avatar_url
status
source_visibility
view_count
like_count
comment_count
published_at
deleted_at
created_at
updated_at
row_version
```

Deferred capabilities:

| Schema area | Deferred phase | Reason |
|---|---|---|
| `thumbnail_url` mutation | Media/storage | Do not accept arbitrary storage URLs before ownership is defined. |
| `project_media` writes | Media/storage | Requires upload, authorization, validation, and cleanup policy. |
| `project_versions` | Release | Depends on version and snapshot readiness workflows. |
| GitHub and snapshots | Source integration | Requires GitHub, workers, object storage, and retries. |
| Likes and comments | Engagement | Phase 2. |
| View tracking | Analytics/projection | Avoid synchronous writes on every public read. |
| Trending/popular sorting | Engagement/discovery | Requires reliable engagement data. |
| Category and Tag administration | Admin/moderation | Separate protected capability. |

## Recommended implementation order

```text
1.  CreateProject
2.  GetProject
3.  UpdateProject
4.  ReplaceProjectTags
5.  ListMyProjects
6.  PublishProject
7.  ArchiveProject
8.  ReopenProject
9.  ChangeProjectVisibility
10. DeleteProject
11. ListProjectCategories
12. ListProjectSubCategories
13. SearchProjectTags
14. SearchPublishedProjects
15. GetPublishedProjectBySlug
```

The completed Phase 1 user journey is:

```text
Choose category and Tags
  -> create a draft Project
  -> edit and preview it
  -> publish it
  -> find it through public discovery
  -> open/share its direct URL
  -> archive or reopen it later
```
