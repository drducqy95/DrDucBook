# P03.T06 - Backup, fallback va visual QA

## Ket qua

Trang thai: `DONE`

## Trien khai

- Backup/restore snapshot chua appearance profile va asset store.
- Restore xong se cleanup asset khong con duoc tham chieu.
- Theme import/export snapshot tuong thich voi local backup pipeline hien co.
- Visual QA bao gom phone/tablet, day/night, engine switch, recreate smoke va final wallpaper contrast fix.
- Screenshot matrix da cap nhat voi ban `p3-wallpaper-tab.png` moi sau khi sua mau nhan preview.

## Verification

- `AppearanceBackupFilesTest`: PASS.
- `:app:compileAppDebugKotlin`: PASS.
- `:app:assembleAppDebug`: PASS.
- Device QA co `p3-recreate-after-fix.png`, `p3-wallpaper-tab.png`, `p3-theme-ink-miuix-night.png`.

## Bang chung

- `app/src/main/java/io/legado/app/data/repository/AppearanceRepository.kt`
- `app/src/main/java/io/legado/app/help/config/ThemePackageManager.kt`
- `app/src/test/java/io/legado/app/data/repository/AppearanceBackupFilesTest.kt`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-recreate-after-fix.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-wallpaper-tab.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-theme-ink-miuix-night.png`

## Rui ro con lai

- Backup target ngoai cloud hien tai van phai giu quy tắc exclusion khi sang phase sync sau nay.
