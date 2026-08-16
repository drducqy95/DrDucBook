# Phase 06 — Media player, download và audiobook

## 1. Mục tiêu

Có playback audio/video thống nhất, download task bền vững, resume/offline playback và import audiobook.

## 2. Phạm vi ảnh hưởng

`domain/model/ResolvedMedia`, `help/media`, Media3 player, `AudioPlayService`, media UI, Room entities/DAO/repository, foreground download service, notification và audiobook import/export.

Phụ thuộc Phase 04 capability VBook; migration mới sau Phase 00 phải nối tiếp version hiện tại.

## 3. Task

### C06.01 — Unified playback

- **Mục tiêu:** audio/video dùng cùng state và media session.
- **Ví dụ:** direct MP4, HLS, audio episode có subtitle, URI lỗi.
- **Thông qua:** play/pause/seek/speed/subtitle/audio focus/background/notification pass; player release sau Activity/service stop.

### C06.02 — Persistent download model

- **Mục tiêu:** download sống qua process death và resume.
- **Thực hiện:** `MediaDownloadTask/Item`, DAO, state machine pending/running/paused/failed/completed/canceled, checkpoint và checksum; migration 103→104 nếu source health đã dùng 103.
- **Ví dụ:** tải 10 episode, mất mạng ở item 4, app bị kill, resume từ item 4.
- **Thông qua:** không duplicate task; cancel dọn file tạm; DB khôi phục đúng sau process death.

### C06.03 — Download service và UI

- **Mục tiêu:** người dùng xem, pause, retry, cancel, xóa và mở file.
- **Thông qua:** foreground notification đúng progress; error có retry; UI không đọc filesystem/network trực tiếp.

### C06.04 — Audiobook import

- **Mục tiêu:** import thư mục/ZIP audio thành book/episode metadata mà không nhầm với novel VBook.
- **Ví dụ:** MP3/M4A có cover, filename theo chapter, file thiếu metadata.
- **Thông qua:** preview mapping, duplicate policy, cancel/rollback và phát offline pass.

### C06.05 — Offline playback/export

- **Mục tiêu:** phát file đã tải khi không mạng và export đúng quyền truy cập.
- **Thông qua:** airplane-mode test pass; URI permission không mất sau restart; file hỏng hiển thị repair/re-download.

## 4. Điều kiện đóng

Media integration, migration, process-death, offline và Nox background playback pass; không có leaked player, foreground service hoặc download task.

## 5. Trạng thái thực thi 2026-07-26

| Task | Trạng thái | Bằng chứng |
|---|---|---|
| C06.01 | DONE | Unified foreground playback service, MediaSession/notification/audio focus, scheme-aware HTTP/local source, persisted progress/speed, PiP và background playback. |
| C06.02 | DONE | Room 104 task/item state machine và migration 103→104; migration instrumentation test pass trên LDPlayer. |
| C06.03 | DONE | Download service/UI hỗ trợ 3 worker, FIFO/reorder, pause/resume/retry/cancel/delete/open, Range resume, HLS checkpoint, expiry refresh và idle auto-stop. |
| C06.04 | DONE | Audiobook import file/folder/M3U/CUE, metadata/cover, preview/edit/select, duplicate guard, transaction rollback và phát SAF offline pass. |
| C06.05 | DONE | Resolver ưu tiên local, tự fallback online khi local hỏng, đánh dấu repair và giữ persistable URI permission. |

Phase 06 đã đóng trên LDPlayer `127.0.0.1:5555`. Bản debug cuối build/install thành công và không có `AndroidRuntime` crash trong các smoke test playback/download/picker.
