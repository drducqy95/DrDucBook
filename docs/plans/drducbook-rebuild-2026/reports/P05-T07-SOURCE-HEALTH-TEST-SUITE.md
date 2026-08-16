# P05.T07 - Source health test suite

## Muc tieu

Dong gate Phase 05 bang bo test tap trung cho engine, adapter Book/RSS/VBook, classification, worker/service, retention, dashboard state va migration schema.

## Thay doi test bo sung

- `SourceCheckClassifierTest`:
  - Them matrix `classifyFailureMessageCoversPhaseGateStatuses`.
  - Cover offline, auth, captcha, rate limit, DNS/network, TLS, broken rule, empty, media, unsupported, stale va HTTP error.
- `SourceHealthProbeRepositoriesTest`:
  - Them `bookAdapterLimitsStagesByProfileDepth`.
  - Them `rssAdapterLimitsStagesByProfileDepth`.
  - Them `vbookAdapterLimitsStagesByProfileDepth`.
  - Khoa Quick/Standard/Full khong chay nham stage qua sau.
- `SourceCheckEngineTest`:
  - Them `checkBookSourceDoesNotMutateSourceEntity`.
  - Xac minh health run khong sua group/comment/rule/enabled cua source.

## Matrix coverage

| Nhom | Bang chung |
|---|---|
| Success va partial/degraded | `ProbeBookSourceUseCaseTest`, `SourceCheckClassifierTest`, `BookSourceHealthModelsTest` |
| Offline/auth/captcha/rate/DNS/TLS/rule/empty/media/stale/http | `SourceCheckClassifierTest`, `BookSourceHealthModelsTest` |
| Book adapter Quick/Standard/Full | `SourceHealthProbeRepositoriesTest` |
| RSS adapter Quick/Standard/Full | `SourceHealthProbeRepositoriesTest`, `SourceCheckEngineTest` |
| VBook plugin/track compatibility | `SourceHealthProbeRepositoriesTest` |
| Engine persistence, timeout va cancellation | `SourceCheckEngineTest` |
| Worker targeted/all-enabled behavior | `BookSourceHealthCheckProcessorTest`, `BookSourceHealthWorkerTest` |
| Foreground session resume/cancel state | `CheckSourceServiceTest`, `CheckSourceSessionStoreTest` |
| Dashboard summary/filter/source details | `SourceHealthStateTest`, `BookSourceHealthRepositoryTest` |
| Retention va source delete cleanup | `SourceCheckRepositoryTest` |
| Source immutability | `SourceCheckEngineTest`, `SourceCheckMigrationTest` |
| Migration schema | `SourceCheckMigrationTest` Android test artifact |

## Kiem thu da chay

Da chay focused unit suite:

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.sourcehealth.SourceCheckClassifierTest" --tests "io.legado.app.data.repository.sourcehealth.SourceHealthProbeRepositoriesTest" --tests "io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest" --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.service.CheckSourceServiceTest" --tests "io.legado.app.service.CheckSourceSessionStoreTest" --tests "io.legado.app.ui.book.source.health.SourceHealthStateTest" --tests "io.legado.app.data.repository.BookSourceHealthRepositoryTest" --tests "io.legado.app.domain.model.BookSourceHealthModelsTest" --tests "io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest" --no-daemon --console=plain
```

Ket qua: BUILD SUCCESSFUL, 33 tests PASS, 0 skipped/failures/errors.

Bang chung unit:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.sourcehealth.SourceCheckClassifierTest.xml` - 3 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceHealthProbeRepositoriesTest.xml` - 6 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckEngineTest.xml` - 5 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest.xml` - 4 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml` - 3 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthWorkerTest.xml` - 2 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.CheckSourceServiceTest.xml` - 2 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.CheckSourceSessionStoreTest.xml` - 1 test PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.book.source.health.SourceHealthStateTest.xml` - 2 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.BookSourceHealthRepositoryTest.xml` - 1 test PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.BookSourceHealthModelsTest.xml` - 2 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest.xml` - 2 tests PASS.

Bang chung Android migration co san:

- `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml` - `SourceCheckMigrationTest`, 1 test PASS, timestamp `2026-07-29T22:56:00`.

## Ghi chu

- Da thu chay lai Android migration test rieng cho `SourceCheckMigrationTest`; lan chay vuot qua cua so 5 phut nen khong tinh la evidence moi.
- Warning Gradle/AGP/Baseline Profile la warning cau hinh hien co, khong phai loi source health.
- Phase 05 gate dat: scheduled Quick, targeted Standard va manual Full deu co engine/test evidence; source data khong bi ghi nguoc; dashboard/source list doc cung repository health.
