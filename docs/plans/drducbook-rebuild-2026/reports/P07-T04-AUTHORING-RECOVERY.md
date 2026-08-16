# P07.T04 - Corruption recovery va autosave history

## Muc tieu

Khong silently drop project loi trong authoring store. Khi manifest/hash/index/asset gap co van de, app phai co diagnostic ro rang va co duong khoi phuc tu snapshot hop le gan nhat.

## Pham vi da tac dong

- `app/src/main/java/io/legado/app/domain/gateway/AuthoringProjectGateway.kt`
- `app/src/main/java/io/legado/app/domain/usecase/AuthoringProjectUseCase.kt`
- `app/src/main/java/io/legado/app/data/repository/AuthoringProjectRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AuthoringProjectFileStore.kt`
- `app/src/test/java/io/legado/app/data/repository/AuthoringProjectFileStoreTest.kt`

## Noi dung trien khai

- Them `AuthoringRecoveryType` va `AuthoringRecoveryDiagnostic` o gateway domain.
- Them API toi thieu: `recoveryDiagnostics()`, `restoreLatestProjectSnapshot(projectId)`, `deleteRecoveryDiagnostic(id)`.
- `AuthoringProjectFileStore` ghi recovery metadata vao `authoring/recovery/*.meta.json`.
- Manifest corrupt/unsupported schema/hash mismatch duoc move vao `authoring/recovery/quarantine/`, sau do store thu restore manifest tu snapshot moi nhat.
- Save project tao snapshot manifest truoc va sau commit, giu retention 5 snapshot moi nhat trong `authoring/recovery/history/{projectId}`.
- Asset index corrupt duoc quarantine va khong lam save/load crash.
- Asset index tro toi file da mat se ghi diagnostic `MISSING_ASSET` co id on dinh, tranh lap diagnostic moi moi lan doc.
- Diagnostic message duoc redact root path; `sourcePath`/`recoveryPath` dung duong dan tuong doi trong authoring root.

## Dieu kien thong qua

- Corrupt manifest khong lam crash va khong lam project bien mat neu co snapshot hop le.
- Hash mismatch khong bi nuot im lang; manifest loi duoc quarantine va snapshot moi nhat duoc restore.
- History retention idempotent: sau nhieu lan save chi giu 5 snapshot moi nhat.
- Corrupt asset index khong pha save/load.
- Missing asset sinh diagnostic va project van load duoc.

## Lenh kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --no-daemon --console=plain`
- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`

## Ket qua

- `AuthoringProjectFileStoreTest`: 10 tests PASS, failures/errors/skipped = 0.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringProjectFileStoreTest.xml`
- `docs/plans/drducbook-rebuild-2026/reports/P07-T04-AUTHORING-RECOVERY.md`

## Rui ro va viec con lai

- UI recovery surface chua duoc thiet ke rieng; hien tai P07.T04 cung cap API/domain diagnostics de Writing/Ebook UI hoac Workspace surface hien thi o P07.T06/P11.
- P07.T05 can loai tru `authoring/recovery/**` khoi backup cloud theo ADR-008, nhung van backup project/assets hop le.
- Snapshot history hien gio luu manifest, chua tach chapter chunk rieng; neu project rat lon, P07.T06 nen them benchmark truoc khi tang retention.
