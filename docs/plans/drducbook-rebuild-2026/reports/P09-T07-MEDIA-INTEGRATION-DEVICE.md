# P09.T07 - Media fixtures, integration va device tests

## Muc tieu

Dong gate P09 cho player/downloader bang bang chung tren JVM, emulator va release artifact: media source/VBook fixture, playback offline, output probe, Room migration, logcat crash scan va R8/release package.

## Pham vi file da tac dong

- `app/src/androidTest/java/io/legado/app/integration/MediaDevicePlaybackSmokeTest.kt`
- `docs/plans/drducbook-rebuild-2026/reports/P09-T07-MEDIA-INTEGRATION-DEVICE.md`
- `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`
- `docs/plans/drducbook-rebuild-2026/PLAN-LOG.md`

## Noi dung hoan thanh

- Them instrumentation smoke test tao file WAV cuc bo trong cache app.
- Xac minh `ExoPlayer` cua Media3 co the prepare -> ready -> play -> pause -> release voi media offline tren device.
- Xac minh output offline tao ra probe duoc bang `MediaExtractor`, co track media hop le.
- Chay lai integration fixture hien co:
  - `VbookMediaIntegrationTest.vbookHlsTrackIsPlayableAndDownloadable`
  - `MediaDownloadMigrationTest.migrate103To105CreatesPersistentDownloadQueueAndResumeIdentity`
- Mo app debug truc tiep qua launcher component thay cho `monkey` vi emulator khong co `/system/bin/monkey`.
- Quet logcat sau connected tests va sau launch app, khong thay crash/runtime/media renderer exception theo cac mau chinh.
- Build release/R8 thanh cong va tao du 4 APK ABI/universal cung mapping.

## Dieu kien thong qua

- Media parser/downloader regression P09.T06 van PASS tren JVM.
- Android test compile PASS.
- Connected instrumentation tren `emulator-5554 - 14` PASS voi 4 tests, 0 failures/errors/skipped.
- App debug launch duoc `MainActivity` bang `am start`.
- Logcat khong co `FATAL EXCEPTION`, `AndroidRuntime`, `Process crashed`, `ExoPlaybackException`, `MediaCodecVideoRenderer`, `MediaCodecAudioRenderer`.
- Release/R8 gate tao APK va mapping.

## Kiem tra da chay

- `.\gradlew.bat :app:compileAppDebugAndroidTestKotlin --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL.
- `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.integration.MediaDevicePlaybackSmokeTest,io.legado.app.integration.VbookMediaIntegrationTest,io.legado.app.MediaDownloadMigrationTest" --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL; 4 tests PASS tren `emulator-5554 - 14`.
- `.\gradlew.bat :app:packageAppRelease --stacktrace`
  - Ket qua: BUILD SUCCESSFUL.
- `.\gradlew.bat :app:assembleAppRelease`
  - Ket qua: BUILD SUCCESSFUL.
- `adb shell am start -W -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`
  - Ket qua: `Status: ok`, `LaunchState: COLD`, `TotalTime: 10533`, `WaitTime: 10560`.
- `adb shell dumpsys meminfo com.drducbook.app.debug`
  - Ket qua chinh: `TOTAL PSS: 363498 KB`, `TOTAL RSS: 455380 KB`, `Activities: 1`, `WebViews: 0`.
- `adb shell dumpsys battery`
  - Ket qua chinh: AC/USB powered, status charging, level 85%.
- `adb logcat -d` voi crash/media filters
  - Ket qua: khong co match.

## Test artifacts

- `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`
  - `tests="4" failures="0" errors="0" skipped="0"`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaDownloadTransferPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaPlaybackPositionPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.ResolvedMediaContractTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaSourceRuleResultParserTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaUriResolverTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookMediaParserTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.MediaDownloadRepositoryTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.media.download.MediaDownloadsStateTest.xml`

## Release artifacts

- `app/build/outputs/apk/app/release/app-app-arm64-v8a-release-unsigned.apk` - 127064579 bytes.
- `app/build/outputs/apk/app/release/app-app-armeabi-v7a-release-unsigned.apk` - 88396392 bytes.
- `app/build/outputs/apk/app/release/app-app-universal-release-unsigned.apk` - 242159484 bytes.
- `app/build/outputs/apk/app/release/app-app-x86_64-release-unsigned.apk` - 149210951 bytes.
- `app/build/outputs/mapping/appRelease/mapping.txt` - 284845658 bytes.

## Rui ro / viec con lai

- Test device moi xac minh local/offline audio Media3 va output probe; chua claim full UI video/PiP/background playback bang thao tac nguoi dung.
- DASH live/dynamic, DRM, multi-adaptation mux va cac nguon can login/cookie that se tiep tuc nam trong P11 regression/manual rollout gate.
- SAF export tap va notification action tap co compile/repository evidence o P09.T06, nhung chua co thao tac UI device rieng trong P09.T07.
- Khong co git repo trong workspace, nen evidence dua tren file hien tai va Gradle/ADB artifacts.

## Ket luan

P09.T07 dat gate media integration/device cho P09. Phase 9 duoc dong voi parser/downloader/playback/recovery/release evidence, giu gioi han ro rang cho cac kich ban video nang can manual/P11.
