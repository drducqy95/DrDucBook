# P07.T06 - Authoring/Ebook test suite

## Muc tieu

Dong gate Phase 07 bang bo test logic, filesystem, export, UI navigation va device integration cho module Sang tac/Bien tap Ebook.

## Thay doi bo sung

- `EbookExportWriter`:
  - EPUB3 cover/image va CBZ image entry duoc copy vao ZIP bang stream thay vi `readBytes()`.
  - Duplicate image-name checksum dung CRC32 theo stream.
- `AuthoringBackupFiles`:
  - Validate asset `sha256` bang stream, tranh doc toan bo asset snapshot vao RAM.
- `AuthoringBackupFilesTest`:
  - Them `repositoryRecreateAndBackupRoundTripLargeAsset` de mo phong process recreate bang repository moi, backup/restore asset lon va so khop tree hash.
- `EbookExportWriterTest`:
  - Them `epubExportStreamsLargeImageWithStableZipMetadata` de khoa asset lon trong EPUB3 voi size/CRC/timestamp on dinh.
- `MainNavigatorTest`:
  - Them smoke cho route `MainRouteWriting`, `MainRouteEbookEditor` va `MainRouteEbookPreview`.

## Matrix coverage

| Nhom | Bang chung |
|---|---|
| CRUD/duplicate/save writing project | `AuthoringProjectUseCaseTest`, `WritingEditOperationsTest` |
| Chapter duplicate/reorder/search/replace | `WritingEditOperationsTest`, `MainNavigatorTest` |
| Atomic filesystem, migration, concurrent save | `AuthoringProjectFileStoreTest` |
| Corruption quarantine/recovery/history | `AuthoringProjectFileStoreTest` |
| Backup/restore, rollback va asset hash | `AuthoringBackupFilesTest` |
| Process recreation style reload | `AuthoringBackupFilesTest.repositoryRecreateAndBackupRoundTripLargeAsset` |
| Preflight duplicate/missing/media/resource/link | `ValidateEbookProjectUseCaseTest` |
| EPUB3/PDF/TXT/HTML/CBZ export | `EbookExportWriterTest`, `EbookExportWriterInstrumentedTest` |
| Large asset/OOM prevention | stream-copy writer/backup code + large asset tests |
| Fixed-layout preview/render | `EbookLayoutRendererTest`, `EbookExportWriterInstrumentedTest` |
| UI route smoke | `MainNavigatorTest` |
| Translation/finalized content to Ebook blocks | `TranslationAuthoringIntegrationTest` |

## Kiem thu da chay

Focused unit suite:

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringBackupFilesTest" --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --tests "io.legado.app.domain.usecase.AuthoringProjectUseCaseTest" --tests "io.legado.app.ui.authoring.writing.WritingEditOperationsTest" --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest" --tests "io.legado.app.service.export.EbookLayoutRendererTest" --tests "io.legado.app.service.export.EbookExportScopeTest" --tests "io.legado.app.domain.model.EbookDocumentTest" --tests "io.legado.app.ui.main.MainNavigatorTest" --no-daemon --console=plain
```

Ket qua: BUILD SUCCESSFUL; 45 tests PASS; failures/errors/skipped = 0.

Device integration suite:

```text
.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.service.export.EbookExportWriterInstrumentedTest,io.legado.app.integration.TranslationAuthoringIntegrationTest" --no-daemon --console=plain
```

Ket qua: BUILD SUCCESSFUL tren `emulator-5554 - 14`; 3 tests PASS; failures/errors/skipped = 0.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringBackupFilesTest.xml` - 4 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringProjectFileStoreTest.xml` - 10 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.AuthoringProjectUseCaseTest.xml` - 4 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.authoring.writing.WritingEditOperationsTest.xml` - 3 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportWriterTest.xml` - 4 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest.xml` - 3 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookLayoutRendererTest.xml` - 1 test PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportScopeTest.xml` - 3 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.EbookDocumentTest.xml` - 3 tests PASS.
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.main.MainNavigatorTest.xml` - 10 tests PASS.
- `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml` - 3 tests PASS.

## Ket luan gate

- Khong con silent data loss trong authoring store theo fault tests hien co.
- EPUB3/PDF/TXT/HTML/CBZ co unit/device export evidence.
- Backup/restore project co checksum/tree-hash evidence.
- UI route vao Sang tac/Ebook/Preview duoc khoa bang navigation smoke.

## Rui ro va viec con lai

- Chua chay EPUBCheck external validator; neu can se them vao P11 release audit.
- Chua co Compose screenshot/device click-through rieng cho Writing/Ebook editor; P11.T02 se chay end-to-end tren app that sau khi cac phase cloud/media/agent dong.
