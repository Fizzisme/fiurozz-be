# API Contract (DRAFT) — Create Project kèm ảnh/video

> **Trạng thái: đang triển khai, chưa deploy.** Tài liệu này để FE dựng form tạo project song song với
> backend. Khi backend xong sẽ cập nhật lại nếu có thay đổi.

## Tóm tắt

- **Một request duy nhất** qua gateway: `POST /api/projects` dạng `multipart/form-data`, gồm thông tin
  project + ảnh + video (nếu có).
- Request JSON thuần như trước **không còn được hỗ trợ**: tạo project bắt buộc phải có ảnh.
- Backend nhận file, upload lên MinIO, rồi lưu project và media trong cùng một transaction. Nếu bất kỳ bước
  nào lỗi, project **không** được tạo.

## Ràng buộc

| | Ảnh | Video |
|---|---|---|
| Số lượng | **tối thiểu 3, tối đa 5** (bắt buộc) | tối đa **1** (không bắt buộc) |
| Định dạng cho phép | `image/jpeg`, `image/png`, `image/webp`, `image/gif` | `video/mp4`, `video/webm` |
| Dung lượng tối đa mỗi file | 5 MB | 50 MB |

- Toàn bộ request tối đa **80 MB**.
- Backend kiểm tra **nội dung thật của file** (magic bytes), không chỉ dựa vào đuôi file hay
  Content-Type do client gửi. Đổi tên `.exe` thành `.png` sẽ bị từ chối.
- `thumbnailUrl` của project = **ảnh đầu tiên** trong danh sách `images` theo thứ tự FE gửi lên. FE muốn
  ảnh nào làm cover thì đặt ảnh đó lên đầu.
- Thứ tự hiển thị (`sortOrder`) = thứ tự FE append vào form: ảnh trước theo đúng thứ tự gửi, video ở cuối.
- FE nên tự chặn số lượng/dung lượng trước khi submit để báo lỗi sớm. Backend vẫn kiểm tra lại.

---

## Request

```
POST /api/projects
Authorization: Bearer <access_token>
Content-Type: multipart/form-data; boundary=...
```

| Part | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `project` | JSON (`application/json`) | ✓ | Cùng các field như `CreateProjectRequest` hiện tại |
| `images` | file, **lặp lại 3–5 lần** cùng tên `images` | ✓ | Thứ tự = thứ tự hiển thị, file đầu tiên là thumbnail |
| `video` | file | – | Tối đa 1 |

### Nội dung part `project`

Giữ nguyên như body JSON của createProject trước đây:

```json
{
  "subCategoryId": "939dbfc5-e00c-40d8-9351-499df2562304",
  "title": "Fiurozz Backend",
  "shortDescription": "A platform for publishing software projects.",
  "description": "The complete project description.",
  "demoUrl": "https://demo.example.com",
  "githubUrl": "https://github.com/fizzisme/fiurozz-be",
  "visibility": "PRIVATE",
  "techStack": ["java", "spring-boot", "postgresql"],
  "features": ["Project catalog", "Project discovery"],
  "tagIds": ["2ed51a2d-3ca7-4463-8402-c82a12255c92"]
}
```

### ⚠️ Lưu ý quan trọng cho FE: part `project` phải có Content-Type `application/json`

Nếu append chuỗi JSON trực tiếp (`formData.append('project', JSON.stringify(data))`), part đó sẽ là
`text/plain` và backend trả **415 Unsupported Media Type**. Phải bọc trong `Blob`:

```ts
const form = new FormData();

form.append(
  'project',
  new Blob([JSON.stringify(projectData)], { type: 'application/json' }),
);

// Thứ tự append = thứ tự hiển thị. images[0] là thumbnail.
images.forEach((file) => form.append('images', file));

if (video) {
  form.append('video', video);
}

await fetch('/api/projects', {
  method: 'POST',
  headers: { Authorization: `Bearer ${accessToken}` },
  // KHÔNG tự set 'Content-Type': browser sẽ tự thêm kèm boundary.
  body: form,
});
```

Với axios cũng vậy: truyền thẳng `FormData`, **không** set header `Content-Type` bằng tay.

---

## Response thành công `201 Created`

Headers `Location` và `ETag: "0"` giữ nguyên như createProject cũ.

Body: giống response createProject cũ, có thêm `thumbnailUrl` (đã có giá trị) và field mới `media`:

```json
{
  "success": true,
  "code": "PROJECT_CREATED",
  "message": "Project created successfully.",
  "data": {
    "id": "ff82810c-bb24-46cf-b25f-48cb96532cda",
    "title": "Fiurozz Backend",
    "slug": "fiurozz-backend",
    "thumbnailUrl": "http://localhost:9000/project-media/projects/ff82810c-.../4b1e...webp",
    "images": [
      "http://localhost:9000/project-media/projects/ff82810c-.../4b1e...webp",
      "http://localhost:9000/project-media/projects/ff82810c-.../9a3c...png",
      "http://localhost:9000/project-media/projects/ff82810c-.../c71d...jpg"
    ],
    "media": [
      { "id": "a1b2...", "mediaType": "IMAGE", "url": "http://localhost:9000/.../4b1e...webp", "sortOrder": 0 },
      { "id": "b2c3...", "mediaType": "IMAGE", "url": "http://localhost:9000/.../9a3c...png",  "sortOrder": 1 },
      { "id": "c3d4...", "mediaType": "IMAGE", "url": "http://localhost:9000/.../c71d...jpg",  "sortOrder": 2 },
      { "id": "d4e5...", "mediaType": "VIDEO", "url": "http://localhost:9000/.../e82f...mp4",  "sortOrder": 3 }
    ],
    "...": "các field còn lại không đổi so với ProjectDetailResponse hiện tại",
    "version": 0
  }
}
```

- `media[].mediaType`: `"IMAGE" | "GIF" | "VIDEO"`.
- `images` (mảng string) được giữ để không phá chỗ nào đang dùng, nhưng **chỉ chứa ảnh**. FE nên chuyển
  sang dùng `media` vì nó có cả video và thứ tự.
- `GET /api/projects/{id}` và các API đọc chi tiết project cũng trả về `media` theo cùng format.

---

## Lỗi

Format lỗi giống các API project hiện tại (`success: false`, `code`, `message`, `errors`).

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Field trong `project` sai; thiếu part `images`; ít hơn 3 hoặc nhiều hơn 5 ảnh; nhiều hơn 1 video; file sai định dạng hoặc nội dung không khớp định dạng; file vượt dung lượng. `errors` sẽ chỉ rõ field nào, vd `{"images": "between 3 and 5 images are required"}` |
| 401 | `AUTHENTICATION_REQUIRED` | Chưa đăng nhập |
| 409 | `PROJECT_SLUG_CONFLICT` | Đã có project trùng slug (sinh từ `title`) |
| 409 | `SUBCATEGORY_NOT_AVAILABLE` / `TAGS_NOT_AVAILABLE` | `subCategoryId` / `tagIds` không hợp lệ |
| 413 | `PAYLOAD_TOO_LARGE` | Tổng request vượt 80 MB. Có thể do gateway hoặc service trả về |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Part `project` không phải `application/json` (xem lưu ý ở trên) |
| 503 | `MEDIA_STORAGE_UNAVAILABLE` | Không upload được lên MinIO. Project không được tạo, FE có thể cho user thử lại |

---

## Gợi ý UX

- Upload video 50 MB qua mạng chậm có thể mất vài chục giây. Gateway cho phép tối đa 120 giây cho request
  này. Nên hiện progress bar (axios `onUploadProgress`) và disable nút submit trong lúc gửi.
- Kiểm tra số lượng/dung lượng/định dạng ở FE ngay khi user chọn file, đừng đợi tới lúc submit.

## Ngoài phạm vi lần này

- Thêm/xoá/sắp xếp lại media **sau khi** project đã được tạo (`PATCH /api/projects/{id}` vẫn chỉ sửa các
  field text như hiện tại).
- Thumbnail và thời lượng cho video.
