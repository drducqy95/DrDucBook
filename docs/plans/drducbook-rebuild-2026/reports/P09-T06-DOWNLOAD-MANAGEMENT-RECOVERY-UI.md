# P09.T06 - Download management va recovery UI

## Muc tieu

Nguoi dung quan ly media download ro rang: list/filter/sort/progress, pause/resume/retry/cancel/delete/export, notification actions va phuc hoi an toan sau process restart.

## Pham vi file da tac dong

- `app/src/main/java/io/legado/app/domain/gateway/MediaDownloadGateway.kt`
- `app/src/main/java/io/legado/app/data/dao/MediaDownloadDao.kt`
- `app/src/main/java/io/legado/app/data/repository/MediaDownloadRepository.kt`
- `app/src/main/java/io/legado/app/service/MediaDownloadService.kt`
- `app/src/main/java/io/legado/app/ui/media/download/MediaDownloadsContract.kt`
- `app/src/main/java/io/legado/app/ui/media/download/MediaDownloadsViewModel.kt`
- `app/src/main/java/io/legado/app/ui/media/download/MediaDownloadsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/src/test/java/io/legado/app/data/repository/MediaDownloadRepositoryTest.kt`
- `app/src/test/java/io/legado/app/ui/media/download/MediaDownloadsStateTest.kt`

## Noi dung hoan thanh

- Them batch API cho media download gateway/repository:
  - `pauseActive()`: PENDING/RUNNING -> PAUSED.
  - `resumeRecoverable()`: PAUSED/FAILED -> PENDING.
  - `cancelActive()`: PENDING/RUNNING/PAUSED/FAILED -> CANCELED va xoa temp file.
  - `reconcileAfterProcessStart()`: RUNNING cu -> PENDING truoc khi worker nhan viec.
- `claimNext()` cap nhat task cha sang RUNNING ngay khi item duoc worker claim, tranh UI hien task PENDING trong khi item dang chay.
- Dọn scratch file phu cua segmented transfer:
  - `${temp.name}.segment`
  - `${temp.name}.dash-segment`
  - Giu lai main temp file `.downloading` de resume.
- `MediaDownloadService`:
  - Chay reconcile truoc khi start worker.
  - Them notification actions: Pause all, Resume all, Cancel all.
  - Notification phan biet queued/recoverable/active de tiep tuc foreground khi con viec co the xu ly.
- `MediaDownloadsViewModel`:
  - Dung batch API thay vi lap tung task cho pause/resume/cancel all.
  - Tach `buildMediaDownloadsUiState()` thanh reducer thuan de test filter/sort/count.
  - Thay hard-coded/toast bi loi ma hoa bang string resource.
- `MediaDownloadsScreen`:
  - Khong hien raw enum `RUNNING/FAILED`; dung label localized.
  - Them tong quan total/active/completed/failed va recoverable.
  - Disable cac nut batch khi khong co task phu hop.
  - Them semantics contentDescription cho progress bar.
  - Them label filter/sort localized.

## Dieu kien thong qua

- State management khong nhay sai:
  - Reducer test xac nhan filter ACTIVE, sort NAME/SIZE va summary count lay tu full list.
- Multi-task/batch:
  - Repository test xac nhan pause/resume/cancel all cap nhat dung item recoverable va khong xoa completed local file.
- Process restart reconcile:
  - Repository test xac nhan RUNNING cu ve PENDING, scratch file bi xoa, main temp file duoc giu de resume, claim tiep chuyen item/task sang RUNNING.
- Notification actions:
  - Compile gate xac nhan action PendingIntent/service command wiring hop le.
- Accessibility labels:
  - Progress bar co contentDescription theo percent hoac indeterminate.

## Kiem tra da chay

- `.\gradlew.bat :app:clean --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL.
- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.MediaDownloadRepositoryTest" --tests "io.legado.app.ui.media.download.MediaDownloadsStateTest" --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL; 4 tests PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.media.MediaDownloadTransferPolicyTest" --tests "io.legado.app.help.media.MediaPlaybackPositionPolicyTest" --tests "io.legado.app.domain.model.ResolvedMediaContractTest" --tests "io.legado.app.help.media.MediaSourceRuleResultParserTest" --tests "io.legado.app.help.media.MediaUriResolverTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --tests "io.legado.app.data.repository.MediaDownloadRepositoryTest" --tests "io.legado.app.ui.media.download.MediaDownloadsStateTest" --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL; 33 focused media/download tests PASS, 0 failures/errors/skipped.

## Test artifacts

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.MediaDownloadRepositoryTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.media.download.MediaDownloadsStateTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaDownloadTransferPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaPlaybackPositionPolicyTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.ResolvedMediaContractTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaSourceRuleResultParserTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.media.MediaUriResolverTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.vbook.VbookMediaParserTest.xml`

## Rui ro / viec con lai

- Chua chay device/manual notification tap, SAF export tap, Android process kill/restart va offline playback thuc te trong task nay; cac gate do se duoc dong o P09.T07/P11.
- Cleanup hien tai xoa scratch file phu duoc track tu `tempPath`; khong xoa toan bo orphan output khong con DB record de tranh xoa nham file nguoi dung.
- Khong co git repo trong workspace, nen evidence dua tren file hien tai va Gradle artifacts.

## Ket luan

P09.T06 dat gate JVM/compile cho download management, notification action wiring va recovery UI. Task tiep theo: P09.T07 media integration/device tests.
