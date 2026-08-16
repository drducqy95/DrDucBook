# P09.T03 - Media3 playback service va UI

## Muc tieu

Hoan thien Media3 playback service/UI theo huong service la owner duy nhat cua player/session/notification, UI Compose dieu khien qua gateway/state, va progress/quality/track controls on dinh.

## Pham vi da xu ly

| Hang muc | Ket qua |
|---|---|
| Service-owned player | `MediaPlaybackService` tiep tuc la owner cua `ResolvedMediaPlayer`, `Player`, `MediaSessionCompat`, notification va foreground service. |
| UI binding | `MediaPlayerViewModel` consume `MediaPlaybackGateway.playbackState`, prepare/play/pause/seek/speed/subtitle/audio/quality qua gateway, screen stateless theo MVI/UDF. |
| DataSource theo type | `ResolvedMediaPlayer` tiep tuc nhan `ResolvedMediaVariant` va subtitles; `MediaUriResolver`/contract tests khoa direct/HLS/DASH/local metadata. |
| Progress resume policy | Them `MediaPlaybackPositionPolicy` cho persisted absolute position, clip-relative snapshot, seek va duration; service dung policy nay khi prepare/seek/publish snapshot. |
| Quality switch | `MediaPlayerViewModel.selectVariant()` giu current relative position khi doi quality/variant thay vi reset ve 0. |
| Audio focus/headset | Service giu `AudioFocusRequestCompat`, pause/duck/resume va route receiver cho noisy/headset/Bluetooth reconnect. |
| Notification/session | Service giu media notification voi previous/play-pause/next/stop va MediaSession state/actions. |
| Error/retry | Player error publish vao snapshot; UI hien retry va ViewModel reload current media. |

## File tac dong

- `app/src/main/java/io/legado/app/help/media/MediaPlaybackPositionPolicy.kt`
- `app/src/main/java/io/legado/app/service/MediaPlaybackService.kt`
- `app/src/main/java/io/legado/app/ui/media/player/MediaPlayerViewModel.kt`
- `app/src/test/java/io/legado/app/help/media/MediaPlaybackPositionPolicyTest.kt`

## Behavior matrix

| Scenario | Gate |
|---|---|
| Persisted absolute position nam trong clip | Resume dung persisted absolute position. |
| Persisted position stale/ngoai clip | Fallback ve `clipStart + requestedRelativeStart`. |
| UI seek/snapshot | UI luon thay position/duration relative voi clip; service seek convert ve absolute. |
| Seek vuot clip end | Service policy bound ve `clipEnd`. |
| Quality change | Prepare variant moi voi `startPositionMs=currentPosition` thay vi reset. |
| Direct/HLS/DASH/local contract | P09.T02 parser/contract regression tiep tuc pass. |

## Lenh kiem tra

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain
```

## Ket qua

- Focused playback/media JVM tests: 19 tests PASS, 0 failures, 0 errors, 0 skipped.
- Kotlin compile ran in the same Gradle task and was BUILD SUCCESSFUL.
- XML evidence:
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaPlaybackPositionPolicyTest.xml`: 4 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.ResolvedMediaContractTest.xml`: 4 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaSourceRuleResultParserTest.xml`: 4 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaUriResolverTest.xml`: 3 tests.
  - `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookMediaParserTest.xml`: 4 tests.

## Rui ro con lai

- Chua chay device playback/PiP/background/headset matrix trong P09.T03; report hien tai chung minh policy/compile/regression JVM, con device gate se thuoc P09.T07.
- Service van dung `MediaSessionCompat` deprecated warning hien co; khong doi trong task nay de giu scope nho.
- Track selection can phu thuoc media thuc te cua ExoPlayer; JVM test hien chua verify track override tren device.
