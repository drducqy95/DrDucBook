# Phase 09 - Media player va download video/audio

## Muc tieu phase

Phan giai dung media tu source rules/VBook, phat Media3 on dinh va download Direct/HLS/DASH co resume, track, hash va quan ly task.

## Pham vi file chinh

- `data/repository/MediaResolverRepository.kt`, `help/media/**`, `help/vbook/**`
- `domain/model/ResolvedMedia*`, media gateways/use cases
- `service/MediaPlaybackService.kt`, `MediaDownloadService.kt`
- `ui/media/player/**`, `ui/media/download/**`
- media task entities/DAO/Room, HLS downloader, fixtures/tests

## Task chi tiet

### P09.T01 - Sua source content-rule media resolution

**Muc tieu:** Khong hien `HD + m3u8 URL` nhu text khi do la media.

**Pham vi file:** `MediaResolverRepository.kt`, BookContent/source-rule adapters, VBook media adapter va resolver fixtures.

**Thuc hien:** Chay BookContent/content rule voi book/chapter/context day du; parse label+URL/JSON/VBook result; giu header/cookie/referrer; khong thay logic text source.

**Dieu kien thong qua:** Fixture anh URL nguoi dung resolve thanh HLS 1920x800; text chapter binh thuong khong bi nhan nham; rule error co stage ro.

**Log:** Ghi fixture/hash, before/after result va tests.

### P09.T02 - Khoa ResolvedMedia contracts

**Muc tieu:** Mot model cho direct/HLS/DASH/local, qualities va tracks.

**Pham vi file:** ResolvedMedia domain models/serialization, media gateways/use cases, parser adapters va golden tests.

**Thuc hien:** URI/type/headers/variants/subtitle/audio/duration/drm-unsupported/download metadata; stable IDs; serialization version va VBook adapter.

**Dieu kien thong qua:** Parser golden tests pass; redirects/relative URL normalize dung; model khong chua transient credential trong persistent log.

**Log:** Ghi schema va fixture matrix.

### P09.T03 - Media3 playback service va UI

**Muc tieu:** Service la owner duy nhat cua player/session/notification.

**Pham vi file:** MediaPlaybackService, player binding/repository, MediaPlayer Contract/ViewModel/Screen va device tests.

**Thuc hien:** DataSource theo type; bind UI state; play/pause/seek/speed/quality/tracks; audio focus/headset; background/PiP; persist progress; error/retry.

**Dieu kien thong qua:** Direct/HLS/DASH/local play; background/PiP/notification; kill/restart resume trong sai so; phone/headset scenarios pass.

**Log:** Ghi device/media matrix, screenshots/video va logcat scan.

### P09.T04 - HLS downloader

**Muc tieu:** Tai offline playlist don/master phuc tap.

**Pham vi file:** HLS parser/downloader/checkpoint/encryption handling, download service adapters va playlist fixtures.

**Thuc hien:** Variant selection; relative/cross-domain segments; redirects/header/cookie; AES-128 key/IV; EXT-X-MAP, BYTERANGE, DISCONTINUITY; retry/resume/checkpoint; safe output.

**Dieu kien thong qua:** Fixture user-provided va synthetic edge cases download/play offline; resume khong tai lai segment hoan tat; key/segment cleanup an toan.

**Log:** Ghi playlist matrix, bytes/hash va retry evidence.

### P09.T05 - Direct/DASH download, resume va export

**Muc tieu:** Hoan thien cac format ngoai HLS.

**Pham vi file:** Direct/DASH download workers/repositories, task entities/DAO, SAF export va network fixtures.

**Thuc hien:** HTTP Range/ETag/Last-Modified; DASH segment manifest; disk-space check; checksum; export SAF; cancel/delete transactional.

**Dieu kien thong qua:** Server co/khong Range, ETag changed, disk full, network loss, cancel/export tests pass; corrupt output khong danh DONE.

**Log:** Ghi server fixtures, resume bytes va output hashes.

### P09.T06 - Download management va recovery UI

**Muc tieu:** Nguoi dung quan ly task ro rang.

**Pham vi file:** Media download Contract/ViewModel/Screen, foreground notifications, task reconciliation va UI/service tests.

**Thuc hien:** Compose/MVI list/filter/progress/pause/resume/retry/cancel/delete/export; notification actions; process restart reconcile DB/files; permission/storage errors.

**Dieu kien thong qua:** State khong nhay sai; multi-task/concurrency pass; orphan/temp cleanup; accessibility labels.

**Log:** Screenshot states va UI/service tests.

### P09.T07 - Media integration/device tests

**Muc tieu:** Dong gate player/downloader tren release artifact.

**Pham vi file:** Media unit/integration/instrumentation fixtures, device evidence va R8/release reports.

**Dieu kien thong qua:** Unit parser/downloader, service integration, device playback/PiP/background, offline output, memory/battery smoke va R8 pass.

**Log:** Full media matrix, report paths va known unsupported DRM.

### P09.T08 - VBook video open/download/player settings hotfix

**Muc tieu:** Sua regression nguon video VBook/Legado bi mo nhu reader/browser va bo sung settings trinh phat giong VBook.

**Pham vi file:** `help/book/BookExtensions.kt`, `help/vbook/VbookPluginAdapter.kt`, `ui/main/MainNavGraph.kt`, `ui/book/info/BookInfoViewModel.kt`, `ui/media/player/**`, `service/MediaPlaybackService.kt`, media strings/tests.

**Thuc hien:** Chuan hoa `Book.type` theo source type truoc khi mo tu Kham pha/BookInfo; VBook parse dung `SearchBook.type`; player co nut tai tap ro rang va sheet cai dat tu phat/tu chuyen/tiep tuc/tua/giu man hinh/am luong/phu de.

**Dieu kien thong qua:** Cached text item thuoc source video mo `MediaPlayer`; `.m3u8` parser media/HLS khong regression; compile/test/APK debug pass.

**Log:** `reports/P09-T08-MEDIA-PLAYER-VBOOK-HOTFIX.md`.

## Gate dong phase

- User HLS fixture resolve/play/download offline.
- Khong lo cookie/header trong log/DB.
- Player/download pass process restart va release build.
