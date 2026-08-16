# P09.T05 - Direct/DASH download, resume va export

## Muc tieu

Hoan thien download ngoai HLS: giu Direct resume/checksum/export hien co, bo sung DASH manifest planner/downloader co checkpoint va retry.

## Pham vi thay doi

- `app/src/main/java/io/legado/app/help/media/MediaDownloadTransferPolicy.kt`
- `app/src/main/java/io/legado/app/service/MediaDownloadService.kt`
- `app/src/test/java/io/legado/app/help/media/MediaDownloadTransferPolicyTest.kt`

## Noi dung trien khai

- Them `DashDownloadPlan` va `DashDownloadSegment`.
- Parser DASH dung XML parser an toan:
  - bat secure processing;
  - chan DOCTYPE;
  - tat external general/parameter entities neu parser ho tro.
- Ho tro DASH static co:
  - `BaseURL` o MPD/Period/AdaptationSet/Representation;
  - chon representation tot nhat theo mime score, `bandwidth`, `height`, `width`;
  - `SegmentList` voi `Initialization`, `SegmentURL`, `range`, `mediaRange`;
  - `SegmentTemplate` voi `initialization`, `media`, `startNumber`, `duration/timescale`, `mediaPresentationDuration`;
  - `SegmentTimeline` voi `S@t`, `S@d`, `S@r`;
  - thay bien `$RepresentationID$`, `$Number$`, `$Number%0Nd$`, `$Bandwidth$`, `$Time$`;
  - chon extension offline `mp4`, `m4a` hoac `webm`.
- `MediaDownloadService` nay xu ly `MediaProtocol.DASH`:
  - tai manifest voi headers hien co;
  - refresh source mot lan khi manifest tra 401/403;
  - tai segment vao scratch file truoc khi append;
  - gui `Range` header cho DASH segment/init co range;
  - checkpoint `segmentIndex` chi tang sau append thanh cong;
  - retry 3 lan moi segment va xoa scratch sau loi/thanh cong;
  - finalize qua probe/checksum hien co.
- Direct download tiep tuc dung logic da co:
  - HTTP Range khi co temp bytes;
  - `206` moi append, `200` thi restart;
  - `416` validation qua `Content-Range`;
  - ETag/Last-Modified/Content-Length identity check;
  - SHA-256 checksum khi complete.
- Export SAF da duoc noi san trong `MediaDownloadsScreen` qua `ActivityResultContracts.CreateDocument` va `ExportFile` effect; task nay giu flow do va khoa bang compile.

## Kiem thu

Lenh da chay:

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain
```

Ket qua:

- `MediaDownloadTransferPolicyTest`: 10 tests PASS, 0 failures/errors/skipped.
- Focused media regression: 29 tests PASS, 0 failures/errors/skipped.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.

Bang chung XML:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaDownloadTransferPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaPlaybackPositionPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.ResolvedMediaContractTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaSourceRuleResultParserTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaUriResolverTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookMediaParserTest.xml`

## Rui ro va viec con lai

- DASH live/dynamic, multi-adaptation mux audio+video rieng, DRM va manifest phuc tap chua duoc claim ho tro day du.
- Output DASH hien la chuoi init/segment cua representation da chon, phu hop fixture fMP4 don representation; can P09.T07 de xac minh offline playback tren thiet bi.
- Export SAF chua co instrumentation test copy ra DocumentProvider that; P09.T07/P11 can them device evidence neu can dong gate release.
