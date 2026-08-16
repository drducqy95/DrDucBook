# Phase 06 — Media Player & Download — Kế hoạch triển khai

Spec gốc: [../PHASE-06-MEDIA-PLAYER-DOWNLOAD.md](../PHASE-06-MEDIA-PLAYER-DOWNLOAD.md)
Wave: **3** | Phụ thuộc: P04 (VBook media)
Ước lượng: 5–7 ngày

---

## 1. Mục tiêu

Unified media playback service cho video/audio (Direct/HLS/DASH), persistent download system
với resume, download management UI, audiobook import SAF, và PiP video.

---

## 2. Trạng thái hiện tại

### Đã có

| Artifact | Trạng thái | Ghi chú |
|---|:---:|---|
| MediaPlayer UI | PARTIAL | Contract/RouteScreen/Screen(13 KB)/ViewModel có |
| Resolver domain | DONE | `ResolvedMedia.kt`, `ResolveBookMediaUseCase.kt`, `MediaResolverGateway.kt` |
| Cache download | PARTIAL | `CacheDownload*` files — batch chapter cache, không persistent task |
| ExoPlayer | DONE | Đã trong dependency (Media3) |
| Audio service | DONE | `AudioPlayService.kt` — dùng cho audio book hiện tại |

### Chưa có

| Artifact | Ưu tiên |
|---|:---:|
| `MediaPlaybackService.kt` (unified) | **CRITICAL** |
| `MediaDownloadTask.kt` (domain) | HIGH |
| `MediaDownloadTaskEntity.kt` | HIGH |
| `MediaDownloadItemEntity.kt` | HIGH |
| `MediaDownloadDao.kt` | HIGH |
| `MediaDownloadGateway.kt` | HIGH |
| `MediaDownloadRepository.kt` | HIGH |
| `MediaDownloadService.kt` | HIGH |
| Media Download UI (Contract/ViewModel/Screen) | HIGH |
| Audiobook import (Models/UseCase/UI) | MEDIUM |

---

## 3. Task chi tiết

### P06.T01 — Unified playback service `[TODO → CRITICAL]`

**File:** `service/MediaPlaybackService.kt` `[NEW]`

**Yêu cầu:**
1. Sole owner ExoPlayer, `MediaSession`, notification
2. Input: bookUrl + chapterIndex + optional variantId
3. `ResolveBookMediaUseCase` → `ResolvedMedia` → build `MediaItem`
4. Routing:
   - Direct URL → Progressive factory
   - HLS → HLS factory
   - DASH → DASH factory
   - Local file → file DataSource
5. Background audio: foreground notification với play/pause/seek/skip
6. Video → foreground notification + PiP support
7. Persist progress: bookUrl, chapterIndex, variantId, position, speed, selected tracks
8. Audio focus: duck, pause on transient loss, resume after phone call
9. Bluetooth/headset: pause on disconnect, resume on reconnect
10. Service auto-stop nếu idle > 30s hoặc user swipe notification

**Tiêu chí pass:**
- Play HLS audio → background → notification controls hoạt động
- Play local file → đúng format, seek hoạt động
- Kill app → restart → resume position (trong ±2 giây)
- Phone call → pause → call end → resume

---

### P06.T02 — Player ViewModel refactor `[PARTIAL → DONE]`

**File:** `ui/media/player/MediaPlayerViewModel.kt` `[MODIFY]`

**Yêu cầu:**
1. ViewModel KHÔNG giữ ExoPlayer reference → delegate qua service binding
2. Controls: play/pause, seek, speed (0.5x–3.0x step 0.1), quality/variant selector, subtitle selector
3. State observe từ service → map vào `MediaPlayerUiState`
4. Error handling: network, timeout, format unsupported, DRM (nếu có)

---

### P06.T03 — Player screen completion `[PARTIAL → DONE]`

**File:** `ui/media/player/MediaPlayerScreen.kt` `[MODIFY]`

**Yêu cầu:**
1. States: loading/buffering overlay, error card, empty/no-media
2. Video: lock controls, orientation auto/manual, full-screen toggle
3. Gestures: trái=brightness (icon + bar), phải=volume (icon + bar), double-tap ±10s
4. Long-press speed (2x while held), release restore
5. PiP: auto-enter khi home press while video playing
6. Subtitle overlay nếu có track
7. Chapter list sheet: current highlighted, tap → seek

---

### P06.T04 — Persistent download domain `[TODO]`

**Files:**
- `domain/model/MediaDownloadTask.kt` `[NEW]`
- `data/entities/MediaDownloadTaskEntity.kt` `[NEW]`
- `data/entities/MediaDownloadItemEntity.kt` `[NEW]`
- `data/dao/MediaDownloadDao.kt` `[NEW]`
- `domain/gateway/MediaDownloadGateway.kt` `[NEW]`
- `data/repository/MediaDownloadRepository.kt` `[NEW]`

**Domain model:**
```kotlin
@Stable data class MediaDownloadTask(
    val id: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val variantId: String?,
    val status: DownloadStatus,
    val progress: Float, // 0–1
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val localPath: String?,
    val createdAt: Long,
    val error: String?,
)
enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }
```

**Entity (Room):**
- `MediaDownloadTaskEntity`: task metadata
- `MediaDownloadItemEntity`: per-segment (HLS) hoặc per-range (direct)

**DAO:** full CRUD + query by status/book + aggregate stats

---

### P06.T05 — Download service `[TODO]`

**File:** `service/MediaDownloadService.kt` `[NEW]`

**Yêu cầu:**
1. Foreground service với notification: title/progress/pause/resume/cancel
2. Max concurrent: 3 tasks
3. Queue: FIFO, user reorder
4. Direct URL: OkHttp range resume (check Accept-Ranges + ETag/Last-Modified)
5. HLS: download manifest → segments → key files → reassemble
6. URL expiry: re-resolve qua `ResolveBookMediaUseCase` + verify identity (content-length, first-bytes match)
7. Temp file `.downloading` → rename sau verify (length + checksum + Media3 probe)
8. Network loss: auto-pause → auto-resume khi network restored (BroadcastReceiver)
9. Storage: check free space trước start; alert nếu < 100MB remaining

---

### P06.T06 — Download management UI `[TODO]`

**Files:**
- `ui/media/download/MediaDownloadContract.kt` `[NEW]`
- `ui/media/download/MediaDownloadViewModel.kt` `[NEW]`
- `ui/media/download/MediaDownloadScreen.kt` `[NEW]`

**Yêu cầu:**
1. Task list: cover thumbnail, title, chapter name, progress bar, status badge
2. Actions: pause/resume individual, cancel, retry failed
3. Batch actions: pause all, resume all, cancel all, delete completed
4. Completed tasks: play offline, export SAF, share, delete file
5. Sort: date, name, status, size
6. Filter: All, Downloading, Completed, Failed
7. Storage used indicator

**Navigation:** `MainRouteMediaDownload` trong `MainNavKey.kt`

---

### P06.T07 — Audiobook import `[TODO]`

**Files:**
- `domain/model/AudiobookImportModels.kt` `[NEW]`
- `domain/usecase/ImportAudiobookUseCase.kt` `[NEW]`
- `ui/book/importer/audiobook/AudiobookImportContract.kt` `[NEW]`
- `ui/book/importer/audiobook/AudiobookImportViewModel.kt` `[NEW]`
- `ui/book/importer/audiobook/AudiobookImportScreen.kt` `[NEW]`

**Models:**
```
AudiobookImportSource — FILE (single), FOLDER (multi-file), CUE_SHEET, M3U_PLAYLIST
AudiobookChapterPreview — title, filePath, duration, order, startOffset
AudiobookMetadata — title, author, narrator, year, cover, genre
```

**Use case:**
1. SAF file/folder picker
2. Auto-detect: single file, folder with ordered files, M3U, CUE
3. Extract metadata: ID3/Vorbis/FLAC tags, cover art
4. Preview chapter list with duration, allow edit title/order/narrator/cover
5. Duplicate detection: exact filename+size OR fuzzy title+author+duration
6. Import tạo Book entry (type=AUDIO) + chapters + copy/link files

**Navigation:** `MainRouteAudiobookImport`

---

### P06.T08 — Offline playback `[TODO]`

**Yêu cầu:**
1. Downloaded media: play trực tiếp từ local file, không re-resolve URL
2. Media player ưu tiên local file nếu tồn tại (fallback online nếu local corrupt)
3. Offline indicator trên chapter list
4. Storage management: hiển thị kích thước, cho phép xóa từng file hoặc batch

---

## 5. Test bắt buộc

### Unit tests mới

| Test case | Priority |
|---|:---:|
| Service audio focus duck/pause/resume | HIGH |
| Bluetooth disconnect → pause | MEDIUM |
| Progress persist ±2 giây accuracy | HIGH |
| HLS segment download + reassemble | CRITICAL |
| Range resume verify etag/content-length | HIGH |
| URL expiry re-resolve + identity match | HIGH |
| Temp → final only after verify | CRITICAL |
| Network loss → auto-pause → auto-resume | HIGH |
| Concurrent download max 3 | HIGH |
| Queue FIFO order | MEDIUM |
| Storage check + low space alert | HIGH |
| Audiobook folder detection + chapter order | HIGH |
| M3U/CUE parse + track mapping | MEDIUM |
| Duplicate audiobook detection | MEDIUM |
| Offline play local file priority | HIGH |
| Corrupt local file → fallback online | HIGH |

### LDPlayer smoke test

| # | Kịch bản |
|:---:|---|
| 1 | Play video qua HLS → PiP → background audio |
| 2 | Play audio → notification controls → chapter skip |
| 3 | Kill app → reopen → resume position |
| 4 | Download video → pause → resume → complete → play offline |
| 5 | Download 4 tasks → max 3 concurrent, 1 queued |
| 6 | Tắt wifi giữa download → auto-pause → bật wifi → auto-resume |
| 7 | Import audiobook folder → preview → edit order → import |
| 8 | Offline mode → play downloaded, chặn chưa download |

---

## 6. Điều kiện đóng phase

- [x] Audio/video play không cần giữ app foreground
- [x] Download resume sau disconnect
- [x] Audiobook import từ file/folder/playlist
- [x] PiP video hoạt động
- [x] Progress persist chính xác

---

## 7. Trạng thái thực thi 2026-07-27

| Task | Trạng thái | Bằng chứng |
|---|---|---|
| P06.T01 | DONE | `MediaPlaybackService` là chủ duy nhất của ExoPlayer, MediaSession, notification và audio focus; hỗ trợ HTTP/HLS/DASH, `content://`, `file://`, background, PiP, persisted position/speed và headset disconnect. |
| P06.T02 | DONE | `MediaPlayerViewModel` chỉ dùng `MediaPlaybackGateway`; player được bind qua `MediaPlaybackConnection`, màn hình không tự tạo/release ExoPlayer. |
| P06.T03 | DONE | Loading/error/buffering, variant, speed, seek, double-tap ±10 giây, PiP và clip CUE theo track đã nối. |
| P06.T04 | DONE | Room DB 105 có task/item, DAO, gateway, repository, state machine, progress/checksum/temp/local path, ETag/Last-Modified/content-length và migration 103→105. |
| P06.T05 | DONE | Foreground service tối đa 3 worker, FIFO/reorder, HTTP Range có kiểm tra identity, HLS AES-128/checkpoint, URL expiry re-resolve, dự phòng 100 MB, probe media và `.downloading`→final. |
| P06.T06 | DONE | Compose MVI Download screen có filter/sort, storage used, progress, pause/resume/retry/cancel/delete/open/share/export, batch actions và lối chạy lại queue. |
| P06.T07 | DONE | SAF file/folder, M3U/CUE, metadata/cover, preview/edit/select, duplicate guard và transaction rollback tạo `BookType.audio|local`. |
| P06.T08 | DONE | Resolver ưu tiên file đã tải, fallback online khi file thiếu/hỏng; URI permission được persist và local SAF phát trực tiếp. |

Xác minh: focused unit tests pass; `MediaDownloadMigrationTest` pass trên LDPlayer; debug APK x86_64 build/install pass; `MainActivity` resumed; picker không treo; audiobook SAF end-to-end phát qua service; queue service rỗng tự dừng sau idle; không có `AndroidRuntime` crash. Phase 06 đóng, các benchmark thời gian khởi động/chạy dài chuyển sang Phase 08.
