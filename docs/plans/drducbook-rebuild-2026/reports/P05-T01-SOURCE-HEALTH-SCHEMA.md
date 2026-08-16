# P05.T01 - Source health contracts va schema

## Muc tieu

Luu lich su moi lan kiem tra nguon theo run/stage rieng, giu bang summary
`book_source_health` hien co cho UI cu va khong ghi loi vao `book_sources`
group/comment.

## Ket qua trien khai

- Tang Room tu 107 len 108.
- Them domain contract `domain/sourcehealth/SourceCheckModels.kt`:
  - `SourceCheckProfile`: `QUICK`, `STANDARD`, `FULL`
  - `SourceCheckRunStatus`: `RUNNING`, `COMPLETED`, `FAILED`, `CANCELED`, `INTERRUPTED`
  - `SourceCheckStageStatus`: `RUNNING`, `PASSED`, `FAILED`, `SKIPPED`, `CANCELED`
  - `SourceCheckRun`, `SourceCheckStageResult`
- Them bang `source_check_runs` va `source_check_stage_results`.
- Them `SourceCheckDao` voi transactional `insertRunWithStages` va `updateRunWithStages`.
- Them `SourceCheckRepository` de observe latest/history/status/profile/stage va upsert summary cu khi run hoan tat.
- Noi `BookSourceHealthCheckProcessor` de moi source checked tao mot `QUICK` run va mot stage `probe`.
- Giu `book_source_health` lam latest summary; khong sua `BookSource` entity trong shallow worker.

## Schema va index

Room schema moi: `app/schemas/io.legado.app.data.AppDatabase/108.json`.

`source_check_runs`:
- Primary key: `id`
- Cac cot chinh: `sourceUrl`, `sourceName`, `sourceGroup`, `profile`, `status`,
  `healthStatus`, `startedAt`, `finishedAt`, `latencyMs`, diagnostic redacted,
  stage counters.
- Index:
  - `index_source_check_runs_sourceUrl_startedAt`
  - `index_source_check_runs_status_startedAt`
  - `index_source_check_runs_profile_startedAt`
  - `index_source_check_runs_finishedAt`

`source_check_stage_results`:
- Primary key: `runId`, `stageKey`
- Foreign key: `runId` -> `source_check_runs.id` with cascade delete.
- Index:
  - `index_source_check_stage_results_runId`
  - `index_source_check_stage_results_status`
  - `index_source_check_stage_results_stageOrder`

## Kiem tra

- `./gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - PASS.
- `./gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --tests "io.legado.app.worker.BookSourceHealthCheckProcessorTest" --tests "io.legado.app.worker.BookSourceHealthWorkerTest" --tests "io.legado.app.domain.usecase.ProbeBookSourceUseCaseTest" --tests "io.legado.app.domain.model.BookSourceHealthModelsTest" --no-daemon --console=plain`
  - PASS.
  - `SourceCheckRepositoryTest`: 2 tests, 0 failures/errors/skipped.
  - `BookSourceHealthCheckProcessorTest`: 3 tests, 0 failures/errors/skipped.
- `./gradlew.bat :app:compileAppDebugAndroidTestKotlin --no-daemon --console=plain`
  - PASS.
- `./gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.sourcehealth.SourceCheckMigrationTest" --no-daemon --console=plain`
  - PASS tren `emulator-5554 - 14`.
  - `SourceCheckMigrationTest`: 1 test, 0 failures/errors/skipped.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.worker.BookSourceHealthCheckProcessorTest.xml`
- `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`
- `app/schemas/io.legado.app.data.AppDatabase/108.json`

## Rui ro va viec tiep theo

- `CheckSourceService` van ton tai va van la deep legacy service; P05.T02-P05.T04 se hop nhat no vao engine stage moi.
- T01 moi ghi stage `probe` cho Book source shallow worker; RSS/VBook va stage detail/toc/content/media thuoc P05.T02.
- Chua co retention/cleanup; P05.T06 se gioi han lich su theo age/count va source delete hook.
