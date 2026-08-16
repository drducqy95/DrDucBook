# Phase 07 - Sang tac va Bien tap Ebook

## Muc tieu phase

Hoan thien hai module tren mot nen tang project luu an toan, khong mat du lieu, co preview va export EPUB3/PDF/TXT lap lai duoc.

## Pham vi file chinh

- `ui/authoring/**`, `domain/usecase/ExportAuthoringProjectUseCase.kt`
- authoring repository/model/storage, `service/export/**`
- image/font/asset handling, SAF RouteScreen, backup/restore
- `app/src/test/**authoring**`, `**ebook**`, export fixtures

## Task chi tiet

### P07.T01 - Versioned va atomic authoring repository

**Muc tieu:** Thay JSON file handling roi rac bang project schema versioned va atomic commit.

**Pham vi file:** Authoring project models/repository/storage, DI, filesystem/migration tests va existing JSON adapter.

**Thuc hien:** Manifest/project/chapter/block/asset refs; temp-write+fsync+rename; per-project lock; content hash; migration dispatcher; repository interface va DI.

**Dieu kien thong qua:** Concurrent save khong corruption; crash giua save phuc hoi ban cu/moi hop le; round-trip tat ca block types; filesystem test pass.

**Log:** Ghi schema version, commit protocol va fault-injection results.

### P07.T02 - Hoan thien module Sang tac

**Muc tieu:** Day du project/chapter editing, autosave, undo/redo va AI hooks qua use case.

**Pham vi:** Authoring Contract/ViewModel/Screen, repository/use cases, Workspace recent integration.

**Thuc hien:** Create/rename/duplicate/delete project/chapter, reorder, text/block edit, asset insert, search/replace, unsaved state; AI khong ghi de user text khong phe duyet.

**Dieu kien thong qua:** Loading/empty/error/recovery pass; undo/redo qua chapter switch; autosave debounce/flush on background; Compose stateless.

**Log:** Ghi workflow tests, screenshots va data-loss scenarios.

### P07.T03 - Ebook editor, preview va export

**Muc tieu:** Bien project thanh ebook co layout/metadata/tai nguyen hop le.

**Pham vi file:** Ebook editor/preview contracts/screens/viewmodels, export use cases/writers/renderers va format fixtures.

**Thuc hien:** Metadata/cover/TOC/page/layout/media editor; preview phone/tablet/fixed/reflow; preflight errors/warnings; EPUB3/PDF/TXT writers; TXT lossy confirmation.

**Dieu kien thong qua:** EPUB validator/ZIP structure pass; PDF pages/resources dung; TXT unicode/line endings dung; export deterministic theo fixture.

**Log:** Ghi output hashes, validator/report va preview screenshots.

### P07.T04 - Corruption recovery va autosave history

**Muc tieu:** Khong silently drop project loi.

**Pham vi file:** Authoring repository recovery/quarantine/history, recovery UI/effects, diagnostics va fault fixtures.

**Thuc hien:** Quarantine corrupt manifest/assets, recovery snapshots co gioi han, UI repair/export raw/delete; diagnostics redacted; cleanup policy.

**Dieu kien thong qua:** Corrupt/truncated/missing asset test khong crash; user nhan thong bao va co duong phuc hoi; history retention idempotent.

**Log:** Ghi fault fixtures, recovery output va rui ro con lai.

### P07.T05 - Backup/sync authoring assets

**Muc tieu:** Snapshot bao toan project va content-addressed assets.

**Pham vi file:** Backup/restore snapshot adapters, authoring asset manifest/dedupe va Supabase Storage/Google Drive conflict integration tests.

**Thuc hien:** Manifest entries/checksum/dedupe; exclude temp/recovery cache theo policy; restore transactional; Supabase/Drive snapshot conflict khong merge project am tham.

**Dieu kien thong qua:** Backup/restore multi-project hash match; interrupted restore rollback; conflict tao local/cloud copy dung.

**Log:** Ghi snapshot size/hash va restore tests.

### P07.T06 - Authoring/Ebook test suite

**Muc tieu:** Dong gate logic, filesystem va UI.

**Pham vi file:** Authoring/Ebook unit, filesystem, export, Compose UI va process-recreation tests/reports.

**Dieu kien thong qua:** Unit/integration tests cover CRUD, reorder, autosave, undo/redo, recovery, preflight, all exports, large asset, OOM prevention va process recreation.

**Log:** Test matrix va report paths.

## Gate dong phase

- Khong con silent data loss.
- EPUB3/PDF/TXT fixture va UI smoke test pass.
- Backup/restore project co checksum evidence.
