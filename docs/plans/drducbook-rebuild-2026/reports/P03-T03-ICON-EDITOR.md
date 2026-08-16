# P03.T03 - IconSlot va trinh chinh icon

## Ket qua

Trang thai: `DONE`

## Trien khai

- Mo rong `IconSlot` cho navigation, Workspace, toolbar, shortcut va reader.
- Cho phep doi icon theo slot trong Personalization, bao gom bundled icon va icon tu nguoi dung.
- Ho tro import PNG, WebP va SVG; SVG di qua sanitizer/raster gate cua asset policy.
- Cho phep scale, padding, tint va background preview cho icon.
- Nối runtime icon cho navigation, Workspace, Browser va reader menu/shortcut surface.

## Verification

- `AppearanceSlotAndWallpaperTest`: PASS.
- `:app:compileAppDebugKotlin`: PASS.
- `:app:assembleAppDebug`: PASS.
- Visual QA co `p3-theme-copper-applied.png`, `p3-theme-forest-applied.png`, `p3-theme-ink-applied.png`, `p3-theme-ink-miuix.png`.

## Bang chung

- `app/src/main/java/io/legado/app/domain/model/IconSlot.kt`
- `app/src/main/java/io/legado/app/ui/personalization/PersonalizationIconTab.kt`
- `app/src/main/java/io/legado/app/ui/widget/components/icon/PersonalizedIcon.kt`
- `app/src/main/java/io/legado/app/ui/main/MainScreen.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookRouteScreen.kt`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-theme-copper-applied.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-theme-forest-applied.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-theme-ink-applied.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-theme-ink-miuix.png`

## Rui ro con lai

- Slot moi them trong tuong lai can giu layout stable va fallback icon de khong vo nav bar/toolbar.
