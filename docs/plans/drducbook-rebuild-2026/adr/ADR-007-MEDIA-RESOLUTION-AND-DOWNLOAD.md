# ADR-007 - Media resolution va download

- Status: Accepted
- Date: 2026-07-29
- Owners: P09

## Context

App da co media code va VBook capability detection, nhung normal source content rule khong luon duoc chay cho video. Playback/download can chung mot hop dong de giu header, cookie, Referer, variant va subtitle dung cach.

## Decision

1. Moi BookSource type deu chay content rule. Ket qua duoc normalize thanh `ResolvedMedia`:
   - `Text(html)` va `Images(items)`.
   - `Direct(url, mime, headers, subtitles, audioTracks)`.
   - `Hls(masterUrl, variants, headers, subtitles, audioTracks)`.
   - `Dash(mpdUrl, representations, headers, subtitles, audioTracks)`.
   - `External(url, reason)` cho iframe/DRM/unsupported.
2. Resolver giu SourceKey va request context; secret headers/cookies la ephemeral references vao broker, khong serialize trong Room/export/log. Redirect/domain policy duoc revalidate.
3. Playback dung AndroidX Media3 ExoPlayer/service/session hien co. UI chon quality/audio/subtitle, PiP/background theo media type; lifecycle/audio focus/noisy/headset va process recreation co tests.
4. Downloader cung dung `ResolvedMedia`: direct file resumable Range/ETag; HLS master/media playlist, key/map/segment, variant/audio/subtitle selection; DASH chi download representation duoc Media3 downloader ho tro. Atomic finalization va SHA/size khi server cung cap.
5. Download khong bypass DRM, signed URL expiry hay source authorization. Khi cookie/ticket het han, broker re-resolve neu policy cho phep; khong ghi token vao job database.
6. Filename/content-disposition duoc sanitize; storage qua SAF/app-private media, check disk space, cancellation va cleanup partial. Export chi khi source/user policy cho phep.
7. VBook runtime evidence co the nang capability sau result thuc, nhung declared TTS/translator khong thanh downloadable BookSource. Normal Legado video va VBook video dung cung resolver/player/downloader.
8. External player chi nhan URL/header co the chia se an toan; neu can cookie/secret header khong export duoc, UI giai thich va giu internal player.

## Public contract

- `ResolvedMedia.schemaVersion = 1` va result codes `AUTH_REQUIRED`, `EXPIRED`, `UNSUPPORTED_DRM`, `RULE_ERROR`, `NETWORK_ERROR`, `NO_MEDIA`.
- Media fixture matrix gom direct audio/video, HLS AES-128/map/subtitle/audio, DASH, redirect, expired auth va corrupt segment.
- Playback/download khong log query token, Cookie, Authorization hay Referer value.

## Alternatives

- Tach resolver playback va download: loai bo vi de mat header/variant va hanh vi lech.
- Hand-roll player/playlist parser: loai bo; dung Media3 va parser san co.
- Ep iframe ve direct URL bang WebView sniffing mac dinh: loai bo vi security/terms khong on dinh.

## Consequences

- Source content pipeline can refactor truoc UI player.
- Download job phai nho media identity, khong nho credential.
- Mot so DRM/iframe source chi ho tro external/browser, duoc phan loai ro thay vi fail im lang.

## Rollback

Feature flag theo media type cho phep quay ve external/browser flow. Download moi co the tat rieng; record schema version giu de cleanup/restore, khong downgrade dang tai am tham.
