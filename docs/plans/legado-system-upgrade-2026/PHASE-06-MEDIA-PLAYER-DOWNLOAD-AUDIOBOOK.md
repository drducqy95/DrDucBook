# Phase 06 — Media Player, download/export và import audiobook

## 1. Kết quả phải đạt

Video/audio từ nguồn Legado hoặc VBook được phân giải và phát thật trong ứng dụng; player có seek, speed, track/subtitle, brightness/volume, background/PiP; media tải xuống có trạng thái bền vững, phát offline và export được; người dùng import được sách nói từ file/thư mục và sắp xếp chapter.

## 2. Phạm vi

### Trong phạm vi

- Hợp nhất runtime player trên Media3/ExoPlayer.
- Direct, HLS, DASH; iframe chỉ mở external/internal Browser nếu không có direct stream.
- Video/audio controls, track, subtitle, brightness, volume, speed và seek.
- Background audio, media session/notification và PiP video.
- Persistent playback progress.
- Download queue bền vững, retry/resume/checksum và offline playback.
- Export bằng SAF/share.
- Import audiobook từ file đơn, nhiều file, thư mục và playlist được hỗ trợ.
- VBook media resolver integration.

### Ngoài phạm vi

- Bypass DRM, geo restriction hoặc signature trái phép.
- Transcode codec tùy ý trong release đầu.
- Tải iframe/website video khi không có stream URL hợp lệ.
- Đồng bộ playback cloud.

## 3. Unified player

### P6.1 — Player ownership

- `MediaPlayerRouteScreen` giữ ActivityResult/lifecycle/PiP.
- `MediaPlayerScreen` stateless; ViewModel không giữ ExoPlayer instance.
- Tạo một `MediaPlaybackController`/service owner của player, MediaSession và notification.
- `ResolvedMediaPlayer` chỉ chuẩn hóa MediaItem/data source/headers, không sở hữu UI state.
- Persist bookUrl, chapterIndex, variantId, position, speed và selected tracks.

### P6.2 — Playback behavior

- Direct URL, HLS và DASH dùng đúng MediaItem mime/protocol.
- Inject headers/referer/cookies từ `ResolvedMediaVariant` vào data source.
- URL có expiry phải re-resolve trước retry hoặc khi resume quá hạn.
- Auto-next chỉ khi chapter kế tiếp có media resolve thành công; lỗi giữ current screen.
- Resume position có ngưỡng: không resume nếu còn dưới 5 giây cuối trừ khi user chọn.
- Audio tiếp tục nền; video vào PiP khi user bật.

### P6.3 — Controls

- Play/pause, previous/next, seek bar, ±10/30 giây.
- Speed 0.5x–3.0x và nhớ theo content kind.
- Chọn quality/variant, audio track và subtitle.
- Subtitle external: WebVTT/SRT; ASS chỉ bật nếu decoder/library hiện có hỗ trợ và đã test.
- Gesture trái điều chỉnh brightness local window; phải điều chỉnh volume stream, không thay global brightness vĩnh viễn.
- Lock controls, orientation và full-screen cho video.
- Loading/buffering/error/empty state rõ ràng; không để màn hình đen.

## 4. Download và export

### P6.4 — Persistent media task

Tạo model task bền vững:

- Task: media identity, book/chapter, variant, destination, status, bytes, error, timestamps.
- Item/segment: URL, headers reference, temp/final path, checksum, retry count và expiry.
- Status: queued, resolving, downloading, paused, completed, failed, cancelled.
- Checkpoint mỗi segment/byte range; process death có thể resume.

### P6.5 — Download engine

- Direct file dùng OkHttp range khi server hỗ trợ.
- HLS tải manifest + segment + key hợp lệ; không hỗ trợ DRM-encrypted playlist.
- DASH chỉ bật offline khi Media3 DownloadService flow pass fixture; nếu chưa hỗ trợ phải disable rõ ràng.
- URL hết hạn gọi resolver lại và đối chiếu media identity trước resume.
- Temp file chỉ đổi tên final sau verify length/checksum và probe Media3.
- Foreground notification có pause/resume/cancel.

### P6.6 — Export/offline

- Completed task có `Phát offline`, `Mở file`, `Export`, `Chia sẻ`, `Xóa`.
- Export dùng SAF và stream copy, không yêu cầu broad storage permission.
- Subtitle/cover/metadata export cùng folder khi user chọn.
- Xóa task và xóa file là hai action tách biệt, có confirm.

## 5. Import audiobook

### P6.7 — Import và mapping

- SAF chọn file đơn, nhiều file hoặc document tree.
- Audio format theo Media3 extractor thực tế: MP3, M4A/AAC, OGG/Opus, FLAC, WAV.
- Đọc metadata title/artist/album/track/disc/duration; fallback filename natural sort.
- Hỗ trợ M3U/M3U8 và CUE khi entry resolve trong phạm vi URI đã cấp.
- Preview danh sách chapter trước tạo book.
- User sửa title, narrator, cover, chapter title và kéo thả order.
- Tạo local book/audiobook với stable URI permission hoặc copy vào app storage theo lựa chọn.

### P6.8 — Integrity

- Missing/unreadable file hiển thị warning, không làm mất project import.
- Duplicate detection theo URI + size/hash; user chọn skip/keep.
- Reorder/update metadata không đổi file gốc trừ khi user chọn export metadata mới.

## 6. File tác động

### File hiện có cần sửa

- `app/src/main/java/io/legado/app/domain/model/ResolvedMedia.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ResolveBookMediaUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/MediaResolverRepository.kt`
- `app/src/main/java/io/legado/app/help/media/ResolvedMediaPlayer.kt`
- `app/src/main/java/io/legado/app/help/media/MediaUriResolver.kt`
- `app/src/main/java/io/legado/app/ui/media/player/MediaPlayerContract.kt`
- `app/src/main/java/io/legado/app/ui/media/player/MediaPlayerViewModel.kt`
- `app/src/main/java/io/legado/app/ui/media/player/MediaPlayerScreen.kt`
- `app/src/main/java/io/legado/app/ui/media/player/MediaPlayerRouteScreen.kt`
- `app/src/main/java/io/legado/app/model/AudioPlay.kt`
- `app/src/main/java/io/legado/app/service/AudioPlayService.kt`
- `app/src/main/java/io/legado/app/service/DownloadService.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookMediaParser.kt`
- `app/src/main/java/io/legado/app/help/vbook/VbookPluginAdapter.kt`
- `app/src/main/java/io/legado/app/model/cache/CacheDownload*`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

### File tạo mới dự kiến

- `app/src/main/java/io/legado/app/domain/model/MediaDownloadTask.kt`
- `app/src/main/java/io/legado/app/data/entities/MediaDownloadTaskEntity.kt`
- `app/src/main/java/io/legado/app/data/entities/MediaDownloadItemEntity.kt`
- `app/src/main/java/io/legado/app/data/dao/MediaDownloadDao.kt`
- `app/src/main/java/io/legado/app/domain/gateway/MediaDownloadGateway.kt`
- `app/src/main/java/io/legado/app/data/repository/MediaDownloadRepository.kt`
- `app/src/main/java/io/legado/app/service/MediaPlaybackService.kt`
- `app/src/main/java/io/legado/app/service/MediaDownloadService.kt`
- `app/src/main/java/io/legado/app/ui/media/download/MediaDownloadContract.kt`
- `app/src/main/java/io/legado/app/ui/media/download/MediaDownloadViewModel.kt`
- `app/src/main/java/io/legado/app/ui/media/download/MediaDownloadScreen.kt`
- `app/src/main/java/io/legado/app/domain/model/AudiobookImportModels.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ImportAudiobookUseCase.kt`
- `app/src/main/java/io/legado/app/ui/book/importer/audiobook/AudiobookImportContract.kt`
- `app/src/main/java/io/legado/app/ui/book/importer/audiobook/AudiobookImportScreen.kt`
- `app/src/main/java/io/legado/app/ui/book/importer/audiobook/AudiobookImportViewModel.kt`

## 7. Test bắt buộc phải pass

### Test hiện có

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaUriResolverTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.vbook.VbookMediaParserTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.model.cache.CacheDownloadQueueTest"
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.model.cache.CacheDownloadStateStoreTest"
```

### Test mới bắt buộc

- ResolvedMedia direct/HLS/DASH/header/referer/expiry.
- ViewModel play/pause/seek/speed/variant/next/error state.
- Playback progress restore và near-end policy.
- Direct range resume, no-range fallback, URL refresh và checksum failure.
- HLS manifest/segment/key parsing và unsupported DRM rejection.
- Process death restore task; pause/cancel/delete semantics.
- Audiobook metadata sort, natural filename sort, M3U/CUE, duplicate và missing file.
- SAF export byte equality và cancellation cleanup.

### Instrumentation/Nox

1. Phát fixture MP4, MP3, HLS và DASH.
2. Seek, đổi speed, quality/audio/subtitle, brightness/volume và rotation.
3. Audio background + notification; video PiP.
4. Kill/restart app và kiểm tra resume progress.
5. Download direct/HLS, pause/resume, phát offline và export.
6. Import nhiều audio file, reorder chapter, mở và phát đúng thứ tự.
7. VBook video/audio source đi hết flow resolve → player → download.

### Gate build

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
.\gradlew.bat :app:connectedAppDebugAndroidTest --no-daemon --console=plain
```

## 8. Điều kiện đóng phase

- Player không còn là UI giả; audio/video thật phát end-to-end.
- Controls, tracks, background/PiP và progress hoạt động.
- Download có checkpoint, offline playback và export.
- Audiobook import tạo book/chapter hợp lệ và phát đúng order.
