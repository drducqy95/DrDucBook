# Phase 08 — Integration, hardening và release

## 1. Mục tiêu

Đóng toàn bộ chuỗi migration/tính năng, chứng minh không crash/ANR/OOM/mất dữ liệu và tạo release APK đã ký, có asset tải ngoài.

## 2. Phạm vi ảnh hưởng

`AppDatabase`, `DatabaseMigrations`, Room schemas/fixtures, integration/security tests, feature flags, diagnostics, localization, Gradle packaging/signing, asset catalog và tài liệu người dùng.

Phụ thuộc tất cả phase trước; có thể viết test song song nhưng chỉ mở release gate sau khi phase được chọn đã pass.

## 3. Task

### C08.01 — Migration chain và golden fixtures

- **Mục tiêu:** nâng DB cũ không mất Book, Chapter, dictionary, AI profile, memory, skill, source health và media task.
- **Ví dụ:** fixture từ version 98/99/100/101 lên 102; sau đó chạy 103/104 trên dữ liệu thật đã mask.
- **Thông qua:** `MigrationTest` khai báo migration thực tế; schema validation pass; fixture before/after có assertion dữ liệu.

### C08.02 — Cross-feature integration

- **Suites:** Agent→book source→add book; VBook→media→download; Translation→revision→Reader; TTS→audio focus; OAuth→Chat/Translation.
- **Thông qua:** happy/error/cancel/process death pass; không có test chỉ kiểm tra UI mà bỏ qua gateway.

### C08.03 — Security và diagnostics

- **Mục tiêu:** permission broker, secret redaction, archive import, WebView/private network, VBook sandbox và backup.
- **Thông qua:** không tìm thấy API key/token/cookie trong log, Room plaintext, backup hoặc diagnostic; mutation không có token bị reject.

### C08.04 — Performance và feature flags

- **Mục tiêu:** có kill switch cho feature rủi ro và benchmark trước/sau.
- **Gate:** picker cold ≤4 giây, translation soak 30 phút không mất chunk, QT benchmark theo Phase 03, no retained Activity/WebView/player/native handle, không tăng APK bất thường.
- **Thông qua:** flag tắt được route/tính năng lỗi và rollback không mất dữ liệu.

### C08.05 — Localization và tài liệu

- **Mục tiêu:** cập nhật hướng dẫn Router/OAuth/Agent/model/TTS/VBook/media/authoring và strings cho loading/error/cancel/permission.
- **Thông qua:** không còn hardcoded user-facing text trong phần mới; English/Vietnamese resource compile; thao tác thủ công có hướng dẫn.

### C08.06 — Build, ký và phát hành

- **Mục tiêu:** build ABI debug/no-R8/release, ký, hash và kiểm tra asset.
- **Thực hiện:** release không bundle NMT/TTS/GGUF; model/từ điển lớn chỉ có link catalog; kiểm tra APK Analyzer theo ABI.
- **Thông qua:** signature verify pass; SHA-256 lưu evidence; release install/launch trên Nox; không có crash/ANR sau smoke matrix.

## 4. Điều kiện đóng

Full release gate pass, Nox smoke matrix có bằng chứng, migration/security/integration/performance pass, APK ký đúng và tài liệu/trạng thái dự án phản ánh code thật.

