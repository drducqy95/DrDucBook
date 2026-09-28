# Phase 02: Go Drive Filesystem & Multi-Provider Client (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: `phase-01-spike-decision-gate.md`

## Objective
Xây dựng lớp adapter hệ thống tập tin trong Go hiện thực `webdav.FileSystem`, ánh xạ các thao tác WebDAV sang Google Drive API v3 (Folder-scoped với Bearer token) và hỗ trợ đọc các public sharing links từ Google Drive, OneDrive, Dropbox và generic HTTP autoindex.

## Scope
| In Scope | Out of Scope |
|---|---|
| Interface `DriveClient` và mock client cho unit test | HTTP WebDAV server listener (Phase 03) |
| OAuth Google Drive v3 client với Bearer token & retry logic | Android Service lifecycle (Phase 05) |
| Public link client cho Google Drive, OneDrive, Dropbox, HTTP | Kotlin UI & Settings (Phase 06) |
| `DriveFileSystem` implements `webdav.FileSystem` | |
| `DriveFileReader` implements `io.ReadSeeker` với HTTP Range | |
| In-memory LRU metadata cache (TTL 5 phút, max 1000 items) | |

## Requirements
### Functional
- [ ] REQ-02.1: `ListFiles(parentID, pageToken)` lấy danh sách tập tin theo phân trang, tự động duyệt tiếp nếu `nextPageToken` tồn tại.
- [ ] REQ-02.2: Bỏ qua hoặc ẩn các file Google Docs/Sheets native (`application/vnd.google-apps.*`) không thể stream trực tiếp.
- [ ] REQ-02.3: `DriveFileReader` hỗ trợ `Seek(offset, whence)` và stream dữ liệu thông qua HTTP header `Range: bytes=start-end`.
- [ ] REQ-02.4: Bộ giải mã public link `PublicLinkClient`:
  - Google Drive: Nhận link folder/file chia sẻ công khai (`/drive/folders/{id}`, `/file/d/{id}/view`).
  - OneDrive: Nhận link công khai (`1drv.ms/*`, `onedrive.live.com/?id=*`).
  - Dropbox: Nhận link chia sẻ folder/file (`dropbox.com/scl/fo/*`, `dropbox.com/scl/fi/*`).
  - HTTP: Nhận URL thư mục có HTML autoindex (Apache/nginx) và bóc tách danh sách file.
- [ ] REQ-02.5: Phân giải đường dẫn Unicode, dấu cách, ký tự đặc biệt tiếng Việt chính xác.
- [ ] REQ-02.6: Cập nhật Bearer access token động mà không cần khởi động lại filesystem.

### Non-Functional
- [ ] NF-02.1: Bộ nhớ đệm không vượt quá 10MB RAM; metadata cache tự động dọn sau 5 phút.
- [ ] NF-02.2: Stream trực tiếp từ remote HTTP response vào socket WebDAV mà không ghi file tạm xuống đĩa.

## Implementation Steps
### Step 1: Core Interfaces & Metadata
1. [ ] Tạo `native/go-webdav/drive/metadata.go` chứa struct `FileMetadata`: ID, Name, Size, ModTime, IsDir, MimeType
2. [ ] Tạo `native/go-webdav/drive/client.go` định nghĩa interface `DriveClient`: `ListFiles`, `GetFile`, `DownloadRange`

### Step 2: Google Drive API Implementation
3. [ ] Viết Google Drive v3 client sử dụng `google.golang.org/api/drive/v3`
4. [ ] Thêm logic quản lý token động: thread-safe mutex khi thay đổi token
5. [ ] Cấu hình timeout 30s và retry backoff khi gặp lỗi mạng tạm thời

### Step 3: Multi-Provider Public Client
6. [ ] Tạo `native/go-webdav/drive/public_client.go`
7. [ ] Hiện thực parser Google Drive public web scraper/API
8. [ ] Hiện thực parser OneDrive public share link API
9. [ ] Hiện thực parser Dropbox shared link content API
10. [ ] Hiện thực parser generic HTTP directory autoindex

### Step 4: WebDAV FileSystem & Seekable Reader
11. [ ] Tạo `native/go-webdav/drive/reader.go` hiện thực `io.ReadSeeker` với chunk streaming và Range headers
12. [ ] Tạo `native/go-webdav/drive/filesystem.go` hiện thực `webdav.FileSystem`:
    - `OpenFile(ctx, name, flag, perm)`: Chỉ cho phép cờ `O_RDONLY`
    - `Stat(ctx, name)`: Tra cứu cache hoặc remote metadata
    - `ReadDir(n)`: Đọc danh sách thư mục con
    - Cấm ghi: `Mkdir`, `RemoveAll`, `Rename` trả về `os.ErrPermission`

### Step 5: Unit Tests
13. [ ] Viết test mock HTTP server mô phỏng Google Drive API, OneDrive, Dropbox, HTTP directory
14. [ ] Test Range seek: seek từ 0, seek từ giữa file, out-of-range
15. [ ] Test concurrent access và token expiry simulation (HTTP 401)

## Files to Create/Modify
- `native/go-webdav/drive/client.go` — Drive client interface & Google Drive implementation
- `native/go-webdav/drive/public_client.go` — Multi-provider public link parser
- `native/go-webdav/drive/filesystem.go` — `webdav.FileSystem` implementation
- `native/go-webdav/drive/metadata.go` — Metadata models and cache
- `native/go-webdav/drive/reader.go` — `io.ReadSeeker` range streaming
- `native/go-webdav/drive/client_test.go` — Unit test suite with mock endpoints

## Test Criteria
- [ ] PASS-02.1: `go test ./native/go-webdav/drive/...` vượt qua 100% tests.
- [ ] PASS-02.2: Mock stream 1GB file đọc thử 5 chunks ngẫu nhiên qua Range request mà heap RAM tiêu thụ < 15MB.
- [ ] PASS-02.3: Public link của Google Drive / OneDrive / Dropbox / HTTP autoindex được parse thành cây thư mục đầy đủ.

---
Next Phase: `phase-03-webdav-server.md`
