# P09.T04 - HLS downloader

## Muc tieu

Hoan thien downloader HLS cho playlist don/master, init map, AES-128, byte-range, discontinuity va resume checkpoint o muc service/parser.

## Pham vi thay doi

- `app/src/main/java/io/legado/app/help/media/MediaDownloadTransferPolicy.kt`
- `app/src/main/java/io/legado/app/service/MediaDownloadService.kt`
- `app/src/test/java/io/legado/app/help/media/MediaDownloadTransferPolicyTest.kt`

## Noi dung trien khai

- Them `HlsByteRange`, `HlsVariant` va metadata `byteRange`, `discontinuityBefore`, `initMap` cho `HlsDownloadSegment`.
- Them parser master playlist va chon variant tot nhat theo `BANDWIDTH`, sau do `RESOLUTION`.
- Parser HLS download nay ho tro:
  - relative segment URL va absolute/cross-domain segment URL;
  - `#EXT-X-MAP:URI=...,BYTERANGE=...`;
  - `#EXT-X-BYTERANGE:length[@offset]` voi offset chaining theo tung resource URL;
  - `#EXT-X-DISCONTINUITY` khong lam hong sequence va duoc danh dau tren segment ke tiep;
  - `#EXT-X-KEY:METHOD=AES-128` voi explicit IV hoac IV sinh tu media sequence;
  - `METHOD=NONE` de quay lai segment khong ma hoa.
- `MediaDownloadService` dung parser variant moi khi resolve master playlist, co loop guard va gioi han depth.
- HLS segment downloader gui `Range` header khi segment/init map co byte-range.
- Segment duoc tai vao scratch file truoc, chi append vao file tam sau khi segment hoan tat. Checkpoint `segmentIndex` chi tang sau append thanh cong, giam rui ro resume ghi trung segment loi.
- Them retry 3 lan cho tung segment va xoa scratch file sau moi loi/thanh cong.
- Them kiem tra dung luong truoc khi append segment vao output HLS.

## Kiem thu

Lenh da chay:

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

Ket qua:

- Focused HLS: 8 tests PASS, 0 failures/errors/skipped.
- Focused media regression: 27 tests PASS, 0 failures/errors/skipped.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.

Bang chung XML:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaDownloadTransferPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaPlaybackPositionPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.ResolvedMediaContractTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaSourceRuleResultParserTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaUriResolverTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookMediaParserTest.xml`

## Rui ro va viec con lai

- Chua co device/offline playback fixture that su cho HLS output; gate nay thuoc P09.T07.
- Downloader van ghi output bang cach append segment da tai xong vao mot media file don, chua remux playlist thanh package rieng. P09.T05/P09.T07 can tiep tuc kiem tra voi HLS fMP4/TS thuc te.
- Header/cookie raw khong duoc them moi vao persistent DB trong task nay; cookie runtime bridge da duoc xu ly o P04/P09.T02 va can duoc xac nhan lai trong gate media end-to-end.
