# P07.T05 - Backup/sync authoring assets

## Muc tieu

Dua authoring projects va content-addressed assets vao snapshot backup/restore co checksum, loai tru recovery/temp cache, va tao nen tang de P10 dong bo cung snapshot qua Supabase/Google Drive.

## Pham vi da tac dong

- `app/src/main/java/io/legado/app/data/repository/AuthoringBackupFiles.kt`
- `app/src/main/java/io/legado/app/data/repository/AuthoringProjectFileStore.kt`
- `app/src/main/java/io/legado/app/help/storage/Backup.kt`
- `app/src/main/java/io/legado/app/help/storage/Restore.kt`
- `app/src/test/java/io/legado/app/data/repository/AuthoringBackupFilesTest.kt`

## Noi dung trien khai

- Them `AuthoringBackupFiles` de copy/validate/restore snapshot authoring theo folder `projects/**` va `assets/**`.
- Snapshot backup verify manifest content hash va asset `sha256/sizeBytes` truoc khi copy.
- Asset trong backup bat buoc dung ten content-addressed `[sha256].[ext]`; asset-index tro file mat/sai hash/sai size se bi reject.
- Loai tru `authoring/recovery/**` va file `.tmp` khoi snapshot, dung policy ADR-008 cho cloud backup.
- Restore vao staging truoc, validate xong moi thay the destination; snapshot invalid khong ghi de local authoring data hien co.
- Noi `Backup` zip hien co them folder `authoring`; `Restore` chi restore authoring khi backup co folder nay, de backup cu van tuong thich.
- Giu Google/WebDAV backup hien huu: authoring folder di cung zip local/WebDAV hien tai; P10 se dung cung snapshot policy cho Supabase va Google Drive appDataFolder.

## Dieu kien thong qua

- Multi-project backup/restore giu du project va asset content-addressed.
- Tree hash cua snapshot backup va restored snapshot khop nhau.
- Recovery/quarantine/history va temp file khong vao backup.
- Snapshot asset bi tamper bi reject truoc khi destination thay doi.
- Restore backup cu khong co `authoring/` khong xoa local authoring data.

## Lenh kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringBackupFilesTest" --tests "io.legado.app.data.repository.AuthoringProjectFileStoreTest" --no-daemon --console=plain`
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AuthoringBackupFilesTest" --no-daemon --console=plain`
- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`

## Ket qua

- `AuthoringBackupFilesTest`: 3 tests PASS, failures/errors/skipped = 0.
- `AuthoringProjectFileStoreTest`: 10 tests PASS trong lenh regression dau tien.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AuthoringBackupFilesTest.xml`
- `docs/plans/drducbook-rebuild-2026/reports/P07-T05-AUTHORING-BACKUP-SYNC.md`

## Rui ro va viec con lai

- Supabase Storage, Google Drive appDataFolder va conflict UI chua nam trong task nay; se hoan tat o P10.T05-P10.T07 theo ADR-008.
- Backup zip hien tai van la legacy local/WebDAV pipeline; P10 se dong goi snapshot cloud v1 co head/revision rieng.
- Chua co device restore smoke cho authoring folder; P07.T06 se dong test suite/QA gate.
