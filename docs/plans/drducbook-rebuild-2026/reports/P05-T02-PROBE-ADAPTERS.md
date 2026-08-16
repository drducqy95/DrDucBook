# P05.T02 - Probe adapters cho Book/RSS/VBook

## Muc tieu

Moi loai nguon co adapter probe rieng, tra evidence theo stage/latency/status,
khong ghi raw HTML/cookie/header secret vao health history va khong coi capability
thieu la loi bat buoc.

## Ket qua trien khai

- Mo rong contract `domain/sourcehealth/SourceCheckModels.kt`:
  - `SourceCheckProbeResult`
  - `SourceCheckStageEvidence`
  - mapper `toStageResult(runId)` / `toStageResults(runId)`
- Them gateway adapter:
  - `BookSourceHealthProbeGateway`
  - `RssSourceHealthProbeGateway`
  - `VbookSourceHealthProbeGateway`
- Them stage runner chung `SourceCheckStageRunner` de do `startedAt`,
  `finishedAt`, `latencyMs`, map loi sang `FAILED`, capability thieu sang
  `SKIPPED` va redact diagnostic.
- Them adapter runtime:
  - `BookSourceHealthProbeRepository`
  - `RssSourceHealthProbeRepository`
  - `VbookSourceHealthProbeRepository`
- Dang ky adapter vao Koin DI trong `appModule.kt`.
- Them deterministic unit fixtures/test trong
  `SourceHealthProbeRepositoriesTest`.

## Capability matrix

| Source type | Quick | Standard | Full | Optional skipped |
|---|---|---|---|---|
| Book | `reachability`, `search`, `explore` | `detail`, `toc` | `content`, `media` | `search`, `explore`, `detail`, `toc`, `content`, `media` neu rule/type thieu |
| RSS | `feed`, `list` | `article` | `content` | `content` neu `ruleContent` thieu; downstream stage skip neu feed/list khong co item |
| VBook | `manifest`, `scripts` | `home`, `search`, `detail`, `toc` | `content`, `track` | Tat ca role/capability khong khai bao tu manifest/profile deu `SKIPPED` |

## Bao mat diagnostic

- `SourceCheckStageRunner` chi luu dong dau tien cua exception message.
- Diagnostic dai, co dau hieu HTML/body hoac chua secret pattern duoc rut gon/redact
  qua `redactBookSourceDiagnostic`.
- Adapter khong luu response body, cookie, full header hay stack trace vao stage
  evidence.

## Kiem tra

- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceHealthProbeRepositoriesTest" --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest" --tests "io.legado.app.domain.model.BookSourceHealthModelsTest" --tests "io.legado.app.help.vbook.VbookPluginInspectorTest" --tests "io.legado.app.help.vbook.VbookMediaParserTest" --no-daemon --console=plain`
  - PASS.
  - `SourceHealthProbeRepositoriesTest`: 3 tests, 0 failures/errors/skipped.
  - `SourceCheckRepositoryTest`: 2 tests, 0 failures/errors/skipped.
  - `BookSourceHealthCheckProcessorTest`: 3 tests, 0 failures/errors/skipped.
  - `BookSourceHealthWorkerTest`: 2 tests, 0 failures/errors/skipped.
  - `ProbeBookSourceUseCaseTest`: 2 tests, 0 failures/errors/skipped.
  - `BookSourceHealthModelsTest`: 2 tests, 0 failures/errors/skipped.
  - `VbookPluginInspectorTest`: 5 tests, 0 failures/errors/skipped.
  - `VbookMediaParserTest`: 4 tests, 0 failures/errors/skipped.

## Bang chung

- `app/src/main/java/io/legado/app/domain/sourcehealth/SourceCheckModels.kt`
- `app/src/main/java/io/legado/app/domain/gateway/SourceHealthProbeGateways.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/SourceCheckStageRunner.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/BookSourceHealthProbeRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/RssSourceHealthProbeRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/sourcehealth/VbookSourceHealthProbeRepository.kt`
- `app/src/test/java/io/legado/app/data/repository/sourcehealth/SourceHealthProbeRepositoriesTest.kt`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceHealthProbeRepositoriesTest.xml`

## Rui ro va viec tiep theo

- P05.T02 moi tao adapter/evidence; worker shallow cu van ghi stage `probe`.
- P05.T03 se tao `SourceCheckEngine` de chon profile Quick/Standard/Full,
  aggregate/classify status va map adapter evidence vao `SourceCheckRun`.
- Deep `CheckSourceService` van can duoc hop nhat qua P05.T03-P05.T04.
