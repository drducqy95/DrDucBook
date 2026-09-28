# Phase 01: WebDAV Branding ("DrDucBook WebDAV") & Google Drive API v3 Native Thumbnails

Status: ⬜ Pending
Dependencies: None

## Objective
1. Chuẩn hóa thương hiệu: Sửa toàn bộ tên hiển thị dịch vụ WebDAV từ "Legado Drive WebDAV" sang "DrDucBook WebDAV" (Notification title, Basic auth realm, chuỗi tài nguyên).
2. Mở rộng engine Go WebDAV (`public_client.go`) để khai thác tính năng native thumbnail và metadata của Google Drive REST API v3, cho phép ứng dụng nhận được đường dẫn ảnh bìa và mô tả của file (PDF, DOC, DOCX, TXT, EPUB...) ngay trong kết quả quét thư mục với 0 byte tải thêm.

## Requirements

### Functional
- [ ] Đổi tên hiển thị thông báo Foreground Service trong `DriveWebDavService.kt` từ `"Legado Drive WebDAV"` sang `"DrDucBook WebDAV"`.
- [ ] Đổi HTTP Basic Auth Realm trong `native/go-webdav/server/server.go` từ `Basic realm="LegadoDriveWebDAV"` sang `Basic realm="DrDucBookWebDAV"`.
- [ ] Bổ sung trường `thumbnailLink`, `description`, `iconLink`, `properties` vào câu truy vấn Google Drive REST API v3 trong `listGDrivePublic`.
- [ ] Mở rộng cấu trúc dữ liệu `FileInfo` trong Go để chứa `ThumbnailURL` và `Description`.
- [ ] Cung cấp cơ chế chuyển giao `ThumbnailURL` sang client Android (WebDAV custom XML properties / headers).
- [ ] Rebuild thư viện Gomobile `go-webdav.aar`.

### Non-Functional
- [ ] Không làm tăng độ trễ quét thư mục.
- [ ] Tương thích ngược với các server WebDAV thông thường (Nextcloud, Alist, OwnCloud).

## Implementation Steps
1. [ ] Sửa `DriveWebDavService.kt:116` đổi `.setContentTitle("DrDucBook WebDAV")`.
2. [ ] Sửa `native/go-webdav/server/server.go` đổi Realm sang `"DrDucBookWebDAV"`.
3. [ ] Sửa `native/go-webdav/drive/public_client.go` mở rộng `fields=files(...)`.
4. [ ] Sửa `native/go-webdav/drive/client.go` cập nhật struct `FileInfo`.
5. [ ] Chạy script `tools/go-webdav-android/build.ps1` để biên dịch lại `app/libs/go-webdav.aar`.
6. [ ] Cập nhật `WebDavFile.kt` và `RemoteBook.kt` trên Android để tiếp nhận `thumbnailUrl` và `description`.

## Files to Create/Modify
- `app/src/main/java/io/legado/app/service/DriveWebDavService.kt` - Đổi tên notification thành DrDucBook WebDAV
- `native/go-webdav/server/server.go` - Đổi realm thành DrDucBookWebDAV
- `native/go-webdav/drive/public_client.go` - Thêm thumbnailLink & description vào API v3
- `native/go-webdav/drive/client.go` - Cập nhật FileInfo struct
- `app/libs/go-webdav.aar` - Rebuild native binary
- `app/src/main/java/io/legado/app/lib/webdav/WebDavFile.kt` - Đọc thumbnail property
- `app/src/main/java/io/legado/app/model/remote/RemoteBook.kt` - Bổ sung thumbnailUrl & intro

## Test Criteria
- [ ] Notification khi khởi chạy WebDAV service hiển thị tiêu đề "DrDucBook WebDAV".
- [ ] Thư mục Google Drive công khai chứa PDF và DOCX trả về `thumbnailUrl` hợp lệ.
- [ ] Coil ImageLoader tải và hiển thị được ảnh thumbnail trực tiếp từ link Google Drive.
