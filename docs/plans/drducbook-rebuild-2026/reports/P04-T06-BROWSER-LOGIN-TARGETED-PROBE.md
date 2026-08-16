# P04.T06 - Dang nhap nguon va targeted probe

## Ket qua

Trang thai: `DONE`

## Trien khai

- `BrowserEffect.SyncLoginAndProbe` da giu `pageUrl` va `sourceUrl`; Browser route nay sync cookie voi scope sourceUrl va enqueue worker target theo dung nguon.
- `BookSourceHealthWorker` nhan inputData `sourceUrl`; neu co target thi chay probe 1 nguon, con khong thi giu nhom dashboard all-enabled nhu cu.
- Them `BookSourceHealthCheckProcessor` de tach logic check all-source va check 1 source, giu upsert health record va failure counter nhu hien tai.
- `SourceHealthViewModel.CheckNow` khong doi hanh vi; van goi nhom all-source cho dashboard.
- Them test cho processor va worker input de khoa viec chi probe dung sourceUrl va dashboard request van trong.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:testAppDebugUnitTest --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest"`: PASS.
- `BookSourceHealthCheckProcessorTest`: 2 tests, 0 failure.
- `BookSourceHealthWorkerTest`: 2 tests, 0 failure.
- `ProbeBookSourceUseCaseTest`: 2 tests, 0 failure.

## Bang chung

- `app/src/main/java/io/legado/app/worker/BookSourceHealthWorker.kt`
- `app/src/main/java/io/legado/app/worker/BookSourceHealthCheckProcessor.kt`
- `app/src/main/java/io/legado/app/ui/browser/BrowserRouteScreen.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/test/java/io/legado/app/worker/BookSourceHealthCheckProcessorTest.kt`
- `app/src/test/java/io/legado/app/worker/BookSourceHealthWorkerTest.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthWorkerTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest.xml`

## Rui ro con lai

- Browser/device smoke cho login-flow thuc te va source isolation van nen chay o P04.T07.
- Neu sourceUrl co trong tam nhung khong ton tai trong book_sources, worker target se no-op thay vi fallback sang all-source.
