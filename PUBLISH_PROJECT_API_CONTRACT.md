# Hợp đồng API: Publish Project (dành cho FE)

> Nguồn: đọc từ code `project-service` (`ProjectCommandController`, `PublishProjectHandler`, `ApiExceptionHandler`) và `api-gateway` (`routes.yaml`, `router.go`). Nếu code thay đổi thì file này cần cập nhật theo.

## 1. Tóm tắt

Chuyển một project từ trạng thái `DRAFT` sang `PUBLISHED` và đặt `visibility` trong cùng một thao tác. Chỉ **chủ sở hữu** project mới publish được.

| | |
| --- | --- |
| Method | `POST` |
| URL (qua gateway) | `/api/projects/{projectId}/publish` |
| Auth | Bắt buộc, `Authorization: Bearer <access_token>` |
| Body | JSON `{ "visibility": "PUBLIC" \| "UNLISTED" \| "PRIVATE" }`. FE luôn gửi |
| Content-Type request | `application/json` |
| Rate limit (gateway) | 45 request/phút, burst 10 (theo user) |
| Timeout (gateway) | 5s |

> Gateway cắt prefix `/api/projects`, nên project-service nhận `POST /{projectId}/publish`. FE chỉ cần gọi đường dẫn có prefix `/api/projects`.

## 2. Request

### Path param

| Tên | Kiểu | Mô tả |
| --- | --- | --- |
| `projectId` | UUID | ID của project cần publish |

### Header

| Header | Bắt buộc | Mô tả |
| --- | --- | --- |
| `Authorization` | Có | `Bearer <access_token>` |
| `If-Match` | Có | Version hiện tại của project, **phải đặt trong dấu ngoặc kép**. Ví dụ: `If-Match: "3"` |

### Body

```json
{ "visibility": "PUBLIC" }
```

| Field | Bắt buộc | Giá trị | Ghi chú |
| --- | --- | --- | --- |
| `visibility` | FE luôn gửi | `PUBLIC`, `UNLISTED`, `PRIVATE` (không phân biệt hoa thường) | Server ghi giá trị này vào project khi publish |

- `PUBLIC`: ai cũng thấy, có trong danh sách public. `UNLISTED`: có link thì xem được, không hiện trong danh sách. `PRIVATE`: chỉ chủ sở hữu.
- Client khác FE không gửi body, hoặc không gửi `visibility`, hoặc để trống thì server dùng mặc định **`PRIVATE`**. Lưu ý: giá trị này **ghi đè** visibility đã chọn lúc tạo project.
- Giá trị khác ba giá trị trên bị từ chối với `400` và `errors.visibility = "visibility must be PUBLIC, UNLISTED, or PRIVATE"`.
- Đổi visibility sau khi đã publish dùng endpoint riêng `PATCH /api/projects/{projectId}/visibility` (ngoài phạm vi tài liệu này).

### Về `If-Match` (optimistic locking)

- Giá trị lấy từ field `version` trong response của lần gọi trước (create, update, get detail) hoặc từ header `ETag`.
- Đúng định dạng: `"3"` (có ngoặc kép). Sai định dạng như `3` hoặc `W/"3"` sẽ bị `400`.
- Nếu version không khớp với server (project đã bị sửa ở nơi khác) thì trả `412`. FE cần lấy lại project mới nhất rồi thử lại.

Ví dụ:

```http
POST /api/projects/0d9c1c4e-6a3b-4a52-9c1f-2f7a6f5b9a10/publish HTTP/1.1
Authorization: Bearer eyJhbGciOi...
If-Match: "3"
Content-Type: application/json

{ "visibility": "PUBLIC" }
```

## 3. Response thành công: `200 OK`

Header:

- `ETag: "<version mới>"`. Sau khi publish thì version tăng, FE phải dùng version mới cho các thao tác tiếp theo.

Body:

```json
{
  "success": true,
  "code": "PROJECT_PUBLISHED",
  "message": "Project published successfully.",
  "data": {
    "id": "0d9c1c4e-6a3b-4a52-9c1f-2f7a6f5b9a10",
    "owner": { "id": "uuid", "displayName": "string", "avatarUrl": "string|null" },
    "category": { "id": "uuid", "key": "string", "slug": "string", "title": "string", "icon": "string" },
    "subCategory": { "id": "uuid", "key": "string", "slug": "string", "title": "string" },
    "title": "string",
    "slug": "string",
    "shortDescription": "string",
    "description": "string",
    "thumbnailUrl": "string|null",
    "images": ["string"],
    "media": [
      { "id": "uuid", "mediaType": "string", "url": "string", "sortOrder": 0 }
    ],
    "demoUrl": "string|null",
    "githubUrl": "string|null",
    "techStack": ["string"],
    "features": ["string"],
    "tags": [{ "id": "uuid", "slug": "string", "displayName": "string" }],
    "status": "PUBLISHED",
    "visibility": "string",
    "sourceVisibility": "string",
    "statistics": { "viewCount": 0, "likeCount": 0, "commentCount": 0 },
    "publishedAt": "2026-10-02T10:00:00Z",
    "createdAt": "2026-10-01T08:00:00Z",
    "updatedAt": "2026-10-02T10:00:00Z",
    "version": 4
  },
  "errors": {},
  "timestamp": "2026-10-02T10:00:00Z"
}
```

Thời gian là ISO-8601 UTC. FE nên lưu `data.version` để dùng cho `If-Match` lần sau.

### Idempotent

Nếu project **đã** `PUBLISHED` và FE gửi đúng `If-Match` là version hiện tại thì server vẫn trả `200` với dữ liệu hiện tại (không publish lại, `publishedAt` giữ nguyên, và `visibility` trong body **không** được áp dụng). Bấm đúp hay retry là an toàn, miễn dùng đúng version.

## 4. Response lỗi

Mọi lỗi dùng chung cấu trúc:

```json
{
  "success": false,
  "code": "PROJECT_STALE_VERSION",
  "message": "…",
  "data": null,
  "errors": {},
  "timestamp": "2026-10-02T10:00:00Z"
}
```

FE nên phân nhánh theo `code`, không theo `message`.

| HTTP | `code` | Khi nào | FE nên làm gì |
| --- | --- | --- | --- |
| 400 | `VALIDATION_FAILED` | `If-Match` sai định dạng hoặc âm. Hoặc nội dung project chưa hợp lệ để publish: title, shortDescription, description trống hoặc quá dài, `demoUrl` không phải http/https, `githubUrl` không phải `https://github.com/...` | Hiện `message` cho user. Gợi ý quay lại sửa project |
| 400 | `VALIDATION_FAILED` (kèm `errors.visibility`) | `visibility` không thuộc `PUBLIC`/`UNLISTED`/`PRIVATE` | Lỗi lập trình phía FE |
| 400 | `MALFORMED_REQUEST` | Body không phải JSON hợp lệ | Lỗi lập trình phía FE |
| 400 | `INVALID_REQUEST_PARAMETER` | `projectId` không phải UUID | Lỗi lập trình phía FE |
| 401 | `AUTHENTICATION_REQUIRED` | Thiếu hoặc hết hạn token | Refresh token rồi gọi lại, hoặc chuyển về đăng nhập |
| 403 | `PROJECT_FORBIDDEN` | User không sở hữu project | Báo không có quyền |
| 404 | `PROJECT_NOT_FOUND` | Project không tồn tại hoặc đã bị xoá | Báo không tìm thấy |
| 409 | `PROJECT_INVALID_STATE` | Project không ở `DRAFT` (ví dụ đã `ARCHIVED`). Chỉ DRAFT mới publish được | Báo trạng thái không cho phép, cập nhật lại UI theo `status` |
| 409 | `SUBCATEGORY_NOT_AVAILABLE` | SubCategory của project không còn active | Cho user đổi SubCategory (update project) rồi publish lại |
| 409 | `TAGS_NOT_AVAILABLE` | Có tag không còn active | Cho user bỏ hoặc đổi tag (`PUT /api/projects/{id}/tags`) rồi publish lại |
| 412 | `PROJECT_STALE_VERSION` | `If-Match` không khớp version hiện tại | Gọi lại `GET` project để lấy version mới, rồi hỏi user có muốn publish tiếp không |
| 429 | (từ gateway) | Vượt rate limit 45/phút | Báo thử lại sau |
| 5xx/504 | (từ gateway) | Upstream lỗi hoặc timeout 5s | Cho phép thử lại. Vì endpoint idempotent nên retry an toàn |

> Lỗi 429/5xx do gateway sinh ra có thể **không** theo cấu trúc `ApiResponse` ở trên. FE nên xử lý theo HTTP status trước, rồi mới đọc `code` nếu body parse được.

## 5. Luồng gợi ý cho FE

1. Tạo project (`POST /api/projects`, multipart) và nhận `data.version` cùng `status: "DRAFT"`.
2. User chọn visibility rồi bấm "Publish": gọi `POST /api/projects/{id}/publish` với `If-Match: "<version>"` và body `{ "visibility": "..." }`.
3. Nhận `200`: cập nhật store bằng `data` (đặc biệt `status`, `publishedAt`, `version`).
4. Nhận `412`: gọi `GET` project, cập nhật version rồi hiển thị lại cho user quyết định.
5. Nhận `409` với `SUBCATEGORY_NOT_AVAILABLE` hoặc `TAGS_NOT_AVAILABLE`: điều hướng user sang bước sửa tương ứng.

Ví dụ gọi bằng `fetch`:

```ts
const res = await fetch(`${API_URL}/api/projects/${projectId}/publish`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${accessToken}`,
    'If-Match': `"${version}"`, // bắt buộc có ngoặc kép
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ visibility }), // 'PUBLIC' | 'UNLISTED' | 'PRIVATE'
});
const body = await res.json();
if (!res.ok) {
  // phân nhánh theo body.code
}
```

## 6. Vấn đề CORS cần xử lý trước khi FE gọi từ trình duyệt

Trong `api-gateway/internal/router/router.go`, cấu hình CORS hiện tại:

- `AllowHeaders` **không có** `If-Match` (`Content-Type` đã có sẵn nên body JSON không bị ảnh hưởng). Trình duyệt sẽ chặn ở bước preflight nên request publish không gửi được.
- `ExposeHeaders` chỉ có `Content-Length`. FE **không đọc được** header `ETag` từ response. Cách tạm là dùng `data.version` trong body.
- `AllowOrigins` hiện chỉ có `http://localhost:3000`.

Cần thêm `If-Match` vào `AllowHeaders` (và `ETag` vào `ExposeHeaders` nếu muốn đọc header) ở gateway. Điều này cũng ảnh hưởng tới các endpoint khác dùng `If-Match` như update, delete và replace tags. Mục này chưa được sửa trong code.
