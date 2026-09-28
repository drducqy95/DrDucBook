# Phase 03: WebDAV Server & Session Auth (Track A - WebDAV)

Status: ⬜ Pending
Dependencies: `phase-02-go-drive-filesystem.md`

## Objective
Hiện thực HTTP WebDAV server chỉ đọc (Read-Only) gắn kết với `DriveFileSystem`, hỗ trợ các phương thức `OPTIONS`, `PROPFIND`, `GET`, `HEAD`, sinh session secret bảo vệ bằng HTTP Basic Auth trên `127.0.0.1`, và hỗ trợ tắt server an toàn (graceful shutdown).

## Scope
| In Scope | Out of Scope |
|---|---|
| HTTP server bind `127.0.0.1:<port>` (port 0 mặc định) | Android Foreground Service (Phase 05) |
| HTTP Basic Authentication middleware với session secret ngẫu nhiên | Gomobile Kotlin bridge (Phase 04) |
| Read-only handler (`OPTIONS`, `PROPFIND`, `GET`, `HEAD`) | UI components (Phase 06) |
| Chặn các phương thức ghi (`PUT`, `DELETE`, `MKCOL`, `MOVE`, `COPY` -> 405) | |
| Chuẩn hóa XML Multistatus response tương thích Jsoup của Legado | |
| Graceful shutdown via `context.Context` | |

## Requirements
### Functional
- [ ] REQ-03.1: Server lắng nghe duy nhất trên loopback `127.0.0.1`. Không chấp nhận kết nối từ IP LAN (ví dụ `192.168.x.x` hoặc `0.0.0.0`).
- [ ] REQ-03.2: Khởi tạo ngẫu nhiên `SessionSecret` (chuỗi hex 32-byte an toàn mã hóa mật mã) khi start server.
- [ ] REQ-03.3: Middleware xác thực: Yêu cầu header `Authorization: Basic <base64(legado:<session_secret>)>`. Trả về `401 Unauthorized` kèm header `WWW-Authenticate: Basic realm="LegadoDriveWebDAV"` nếu thiếu hoặc sai secret.
- [ ] REQ-03.4: Phương thức `OPTIONS` trả về header `DAV: 1`, `Allow: OPTIONS, PROPFIND, GET, HEAD`.
- [ ] REQ-03.5: Phương thức `PROPFIND`:
  - Hỗ trợ header `Depth: 0` và `Depth: 1`.
  - Phản hồi XML Multistatus chứa đầy đủ các thuộc tính: `displayname`, `getcontentlength`, `getlastmodified`, `creationdate`, `resourcetype`, `getcontenttype`.
  - Thư mục phải có `<resourcetype><collection/></resourcetype>` và kết thúc bằng `/`.
- [ ] REQ-03.6: Phương thức `GET` hỗ trợ header `Range: bytes=start-end`, trả về HTTP 206 Partial Content kèm `Content-Range`.
- [ ] REQ-03.7: Các phương thức ghi `PUT`, `DELETE`, `MKCOL`, `MOVE`, `COPY` lập tức trả về HTTP 405 Method Not Allowed.
- [ ] REQ-03.8: Hàm `Stop()` đóng listener và chờ các connection đang hoạt động hoàn tất tối đa 5 giây.

### Non-Functional
- [ ] NF-03.1: Không để rò rỉ session secret hoặc access token trong log hay error messages.
- [ ] NF-03.2: Thời gian phản hồi `PROPFIND` danh mục 100 files < 500ms khi đã có cache.

## Implementation Steps
### Step 1: Cấu hình Server & Session Secret
1. [ ] Tạo `native/go-webdav/server/config.go` định nghĩa struct `Config` và helper sinh random 32-byte hex secret
2. [ ] Tạo `native/go-webdav/server/errors.go` định nghĩa các mã lỗi chuẩn

### Step 2: Auth Middleware & Method Filtering
3. [ ] Tạo `native/go-webdav/server/server.go`
4. [ ] Viết middleware kiểm tra Basic Auth so khớp với session secret
5. [ ] Viết middleware kiểm tra method: chỉ cho phép `OPTIONS`, `PROPFIND`, `GET`, `HEAD`

### Step 3: XML Multistatus Formatter
6. [ ] Cấu hình `golang.org/x/net/webdav.Handler` kết nối với `DriveFileSystem`
7. [ ] Viết interceptor hoặc custom XML encoder nếu cần đảm bảo namespace và format tương thích 100% với `WebDav.kt`

### Step 4: Graceful Shutdown & Lifecycle
8. [ ] Quản lý `http.Server` với channel shutdown và context timeout
9. [ ] Lưu trữ port thực tế được cấp phát sau khi bind listener

### Step 5: Protocol Contract Test
10. [ ] Viết unit tests kiểm tra:
    - Truy cập không có Basic Auth -> 401
    - Truy cập với sai secret -> 401
    - Truy cập đúng secret -> 200/207
    - Gọi `PUT` hoặc `DELETE` -> 405
    - Gọi `GET` với `Range: bytes=0-100` -> 206 với 101 bytes
    - Shutdown server sạch sẽ, giải phóng port

## Files to Create/Modify
- `native/go-webdav/server/server.go` — WebDAV server and HTTP handler
- `native/go-webdav/server/config.go` — Server config and secret generation
- `native/go-webdav/server/errors.go` — Error definitions
- `native/go-webdav/server/server_test.go` — Protocol verification test suite

## Test Criteria
- [ ] PASS-03.1: `go test ./native/go-webdav/server/...` vượt qua 100% tests.
- [ ] PASS-03.2: `curl` trên localhost không secret trả về 401; có secret trả về 207 Multi-Status.
- [ ] PASS-03.3: Lớp client Kotlin `WebDav.kt` kết nối thử nghiệm đọc danh sách và tải file thành công 100%.

---
Next Phase: `phase-04-gomobile-bridge-aar.md`
