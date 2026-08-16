# P07.T01 - Versioned va atomic authoring repository

## Muc tieu

Thay co che luu JSON roi rac cua Sang tac/Ebook bang storage co schema version, commit atomic, hash noi dung, asset store rieng va migration tu file JSON cu.

## Thay doi chinh

- Them `AuthoringProjectFileStore` lam lop filesystem rieng cho authoring:
  - Root: `filesDir/authoring`.
  - Project moi: `projects/{projectId}/manifest.json`.
  - Asset rieng: `assets/{projectId}/{sha256}.{ext}`.
  - Asset index: `assets/{projectId}/asset-index.json`.
  - Legacy raw JSON duoc migrate tu `projects/{projectId}.json` sang manifest moi, sau do dua vao `legacy-projects/`.
- Manifest project co:
  - `schemaVersion = 1`.
  - `project`.
  - `assets`.
  - `contentHash` SHA-256 cua project JSON canonical.
  - `savedAt`.
- Commit protocol:
  - Ghi vao `*.tmp` cung thu muc dich.
  - Flush + `FileDescriptor.sync()`.
  - `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
  - Fallback sang `REPLACE_EXISTING` neu filesystem khong ho tro atomic move.
- Repository dung per-project `Mutex` qua `ConcurrentHashMap<String, Mutex>` de serialize save/delete/import theo tung project.
- `AuthoringProjectRepository` van expose `AuthoringProjectGateway` nhu cu, UI/use case hien tai khong can doi.
- DOI voi polymorphic Ebook block, schema moi dung discriminator `blockType` de khong trung field `type` cua `EbookDividerBlock`; migration van co `legacyJson` de doc raw JSON cu.
- DI binding ep kieu `get<Context>()` de tranh nham constructor test-only `File`.

## Kiem thu

Da chay:

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.AuthoringProjectUseCaseTest" --tests "io.legado.app.domain.usecase.ValidateEbookProjectUseCaseTest" --tests "io.legado.app.domain.model.EbookDocumentTest" --tests "io.legado.app.service.export.EbookLayoutRendererTest" --tests "io.legado.app.service.export.EbookExportWriterTest" --tests "io.legado.app.ui.workspace.WorkspaceStateTest" --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

Ket qua:

- `AuthoringProjectFileStoreTest`: 5 tests PASS.
- Cum Authoring/Ebook/Workspace lien quan: 12 tests PASS.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.

Bang chung:

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringProjectFileStoreTest.xml`

## Fault-injection va coverage

- Round-trip tat ca block types hien co: paragraph, heading, quote, image, divider, page break, code, list.
- Migration raw JSON cu sang `manifest.json` co version/hash.
- Crash giua save: `manifest.json.tmp` bi bo qua, manifest cu van doc duoc.
- Import asset dung content hash, dedupe cung bytes va cap nhat manifest asset refs.
- Concurrent save cung project qua repository khong lam hong manifest va manifest van decode duoc.

## Rui ro/cong viec con lai

- P07.T04 se can quarantine/recovery UI cho manifest that su corrupt hoac hash mismatch; P07.T01 moi skip file hong khi load de tranh crash.
- P07.T05 se noi asset index nay vao Supabase/Google Drive backup snapshot.
- Chua tach chapter thanh file rieng; manifest hien van giu `AuthoringProject` day du de giu UI/export hien tai on dinh. Neu project rat lon, P07.T04/P07.T06 nen them benchmark va chunking sau.
