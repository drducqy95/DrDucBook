# P07.T02 - Hoan thien module Sang tac

## Muc tieu

Hoan thien workflow sang tac tren nen repository P07.T01: CRUD project/chapter, duplicate, reorder, edit text, search/replace, undo/redo, autosave va asset insert, dong thoi giu AI suggestion khong ghi de text neu nguoi dung chua phe duyet.

## Thay doi chinh

- Mo rong `WritingContract`:
  - State cho replace query/result, undo/redo va autosave.
  - Intent duplicate project/chapter, undo/redo, replace next/all, flush autosave va image picker.
  - Effect `OpenImagePicker`.
- Mo rong `WritingViewModel`:
  - Per-screen undo/redo stack gioi han 50 snapshot.
  - Autosave debounce 2 giay va flush khi lifecycle `ON_STOP`.
  - Dirty revision guard de autosave xong khong ghi de state moi hon.
  - Duplicate project qua `AuthoringProjectUseCase.duplicate`.
  - Duplicate chapter, search/replace literal va insert image marker `[image:path]`.
  - AI draft van chi ap dung vao prewriting/chapter khi co intent `ApplyAiSuggestion`.
- Them `WritingEditOperations` de tach logic search/replace/duplicate chapter khoi Compose.
- Cap nhat `WritingScreen`:
  - Nut duplicate project tren topbar.
  - Nut undo/redo/duplicate chapter/insert image trong editor.
  - Panel replace trong chapter.
  - Bottom bar hien autosave pending/saving/failed/saved.
  - RouteScreen dung launcher image va lifecycle observer de flush autosave.
- Them string resources cho cac UI action moi.
- Them test cho duplicate project va edit operations.

## Kiem thu

Da chay:

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AuthoringProjectUseCaseTest" --tests "io.legado.app.ui.authoring.writing.WritingEditOperationsTest" --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

Ket qua:

- `AuthoringProjectUseCaseTest`: 4 tests PASS.
- `WritingEditOperationsTest`: 3 tests PASS.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.AuthoringProjectUseCaseTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.authoring.writing.WritingEditOperationsTest.xml`

## Workflow coverage

- Create/open/delete project: da co tu truoc va tiep tuc giu.
- Rename project: edit title/author/description trong editor.
- Duplicate project: tao id moi, copy chapter content va save vao repository.
- Add/delete/move/duplicate chapter: co UI va ViewModel intent.
- Search/replace: literal search, replace next, replace all, dem match.
- Undo/redo: snapshot tren project/chapter/prewriting hien tai.
- Autosave: debounce khi edit va flush on stop.
- Asset insert: image picker luu asset qua repository/usecase va chen marker vao chapter.
- AI safety: output nam o `aiSuggestion`; khong thay chapter/prewriting cho den khi bam Apply.

## Rui ro/cong viec con lai

- Chua co Compose UI/device screenshot cho Writing trong task nay; P07.T06 can bo sung UI/process recreation smoke.
- Undo/redo hien la snapshot noi bo ViewModel, chua persist qua process death; P07.T04 autosave history/recovery se xu ly retention sau.
- Image marker la plain-text insertion cho Writing; P07.T03 se nang cap thanh block/resource flow day du trong Ebook editor/export.
