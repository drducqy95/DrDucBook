# P07.T03 - Ebook editor, preview va export

## Muc tieu

Hoan thien duong bien tap/preview/export ebook tren nen repository P07.T01-P07.T02: metadata, cover, media, preflight, EPUB3/PDF/TXT va hanh vi hien thi trong Ebook editor.

## Pham vi da tac dong

- `app/src/main/java/io/legado/app/service/export/EbookExportWriter.kt`
- `app/src/main/java/io/legado/app/service/export/EbookLayoutRenderer.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ExportAuthoringProjectUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ValidateEbookProjectUseCase.kt`
- `app/src/main/java/io/legado/app/ui/authoring/ebook/EbookEditorScreen.kt`
- `app/src/test/java/io/legado/app/service/export/EbookExportWriterTest.kt`
- `app/src/test/java/io/legado/app/domain/usecase/ValidateEbookProjectUseCaseTest.kt`

## Noi dung trien khai

- TXT export dung LF co dinh va normalize CRLF/CR trong intro/chapter text de output lap lai duoc tren Windows/Android.
- EPUB3 export dat timestamp ZIP entry co dinh, giu dung extension/media-type cua cover, danh dau cover-image cho cover PNG/JPG/WebP dung file goc.
- EPUB3 image packaging tranh de file khi hai anh trung ten; suffix sinh tu checksum noi dung anh nen khong phu thuoc duong dan temp.
- HTML/EPUB image embed dung MIME day du, bao gom `image/svg+xml` khi can.
- Preview/render HTML normalize CRLF trong paragraph inline de ket qua preview/export thong nhat.
- Export/preflight chap nhan `file://` local path cho image/font/resource; resource orphan check so sanh bang local path da chuan hoa.
- UI clone downloaded book bo ky tu separator bi mojibake va dung resource `authoring_chapter_count`.

## Dieu kien thong qua

- EPUB3 ZIP structure pass o unit test: co `mimetype`, container/content/nav, cover PNG manifest dung MIME/properties, hai anh trung ten duoc ghi thanh hai entry rieng.
- EPUB3 deterministic pass o unit test: cung payload ghi 2 lan cho byte array giong nhau va tat ca ZIP entry co `time == 0`.
- TXT unicode/line ending pass o unit test: CRLF/CR input duoc xuat bang LF.
- Preflight pass o unit test: `file://` image/resource hop le khong bi bao missing/corrupt/orphan.
- PDF runtime pass tren emulator: fixed-layout PDF giu page boundary va kich thuoc viewport; all-modern-format smoke tao du TXT/HTML/EPUB3/PDF/CBZ voi nhan localized introduction.

## Lenh kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest" --tests "io.legado.app.service.export.EbookLayoutRendererTest" --tests "io.legado.app.service.export.EbookExportScopeTest" --tests "io.legado.app.domain.model.EbookDocumentTest" --no-daemon --console=plain`
- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
- `.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.service.export.EbookExportWriterInstrumentedTest" --no-daemon --console=plain`

## Ket qua

- Unit tests PASS: `EbookExportWriterTest` 3 tests; `ValidateEbookProjectUseCaseTest` 3 tests; `EbookLayoutRendererTest` 1 test; `EbookExportScopeTest` 3 tests; `EbookDocumentTest` 3 tests.
- Kotlin compile PASS: `:app:compileAppDebugKotlin` BUILD SUCCESSFUL.
- Instrumented tests PASS tren `emulator-5554 - 14`: `EbookExportWriterInstrumentedTest` 2 tests, failures/errors/skipped = 0.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportWriterTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookLayoutRendererTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.service.export.EbookExportScopeTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.model.EbookDocumentTest.xml`
- `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`
- `app/build/reports/androidTests/connected/debug/flavors/app/io.legado.app.service.export.EbookExportWriterInstrumentedTest.html`

## Rui ro va viec con lai

- Chua co screenshot/manual Compose smoke cho Ebook editor/preview tren phone/tablet; P07.T06 se dong UI smoke va process recreation.
- Chua chay external EPUB validator rieng; hien co ZIP/manifest/unit/instrumented smoke. Neu can strict EPUBCheck, bo sung vao P07.T06 hoac CI.
- P07.T04 van can autosave history/recovery khi manifest/assets hong.
