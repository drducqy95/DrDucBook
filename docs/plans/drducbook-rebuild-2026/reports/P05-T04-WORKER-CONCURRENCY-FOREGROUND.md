# P05.T04 - Worker, concurrency, backoff va foreground run

## Muc tieu

Hop nhat luong chay check nguon thanh mot co che foreground co the pause/resume/cancel,
resume duoc sau restart va khong spam cung domain.

## Ket qua trien khai

- `CheckSource.kt` truyen `EXTRA_SELECTED_IDS`, `EXTRA_PROFILE`, `EXTRA_TIMEOUT_MS`
  vao `CheckSourceService`.
- `CheckSourceSessionStore` luu/nap/clear session `CheckSourceSession` trong shared
  preferences de resume sau crash/restart.
- `CheckSourceService`
  - khoi tao dispatcher trong `onCreate()`
  - luu session truoc/sau pause-resume
  - add notification actions `pause` / `resume` / `cancel`
  - resume stored session neu process bi kill giua chuyen quet
  - group check theo registrable domain va backoff cho source cung host
- `BookSourceHealthCheckWorker`
  - periodic 24h
  - one-time manual work theo `sourceUrl`
  - giu exact-target check, khong fallback sang all-enabled khi co target
  - offline short-circuit
- `BookSourceHealthCheckProcessor`
  - cap concurrency theo domain-group
  - same-domain backoff de giam burst request
  - `checkSource(sourceUrl)` chi check dung target source
- `SourceCheckEngine`
  - timeout duoc classify thanh error ro rang thay vi crash
  - cancellation luu run `CANCELED` ma khong cap nhat summary

## Kiem tra

- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.CheckSourceSessionStoreTest" --tests "io.legado.app.service.CheckSourceServiceTest" --tests "io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --no-daemon --console=plain`
  - `CheckSourceSessionStoreTest`: 1 test, 0 failures/errors/skipped.
  - `CheckSourceServiceTest`: 2 tests, 0 failures/errors/skipped.
  - `SourceCheckEngineTest`: 4 tests, 0 failures/errors/skipped.
  - `BookSourceHealthCheckProcessorTest`: 3 tests, 0 failures/errors/skipped.
  - `BookSourceHealthWorkerTest`: 2 tests, 0 failures/errors/skipped.

## Bang chung

- `app/src/main/java/io/legado/app/model/CheckSource.kt`
- `app/src/main/java/io/legado/app/service/CheckSourceSessionStore.kt`
- `app/src/main/java/io/legado/app/service/CheckSourceService.kt`
- `app/src/main/java/io/legado/app/worker/BookSourceHealthCheckProcessor.kt`
- `app/src/main/java/io/legado/app/worker/BookSourceHealthWorker.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckEngine.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.CheckSourceSessionStoreTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.CheckSourceServiceTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthWorkerTest.xml`

## Rui ro va viec tiep theo

- Con co the tach ro hon concurrency lay tu config nguoi dung neu muon lam
  injected/lazy config de de test hon.
- P05.T05 se dua source health dashboard vao UI va Browser/source list.
