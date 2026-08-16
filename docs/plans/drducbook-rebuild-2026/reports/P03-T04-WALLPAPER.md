# P03.T04 - Hinh nen toan app va theo module

## Ket qua

Trang thai: `DONE`

## Trien khai

- Ho tro wallpaper global va override theo module cho Home, Bookshelf, Workspace, Agent, Authoring, Ebook va Reader.
- Cho phep `day/night`, `cover/contain`, alignment ngang/doc, opacity, blur, overlay va dim.
- `AppScaffold` va preview bridge doc cung contract, giu Compose va legacy View dong nhat.
- Tab wallpaper co preview truc tiep va nhan preview da doi sang mau tuong phan khi chua co asset.
- Preview screen hien thi canh bao contrast khi profile hien tai co nguy co that bai do tuong phan.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:assembleAppDebug`: PASS.
- Visual QA co `p3-wallpaper-tab.png`, `p3-preview-tab.png`, `p3-theme-ink-miuix-night.png`.

## Bang chung

- `app/src/main/java/io/legado/app/ui/personalization/PersonalizationWallpaperTab.kt`
- `app/src/main/java/io/legado/app/ui/personalization/PersonalizationPreviewTab.kt`
- `app/src/main/java/io/legado/app/domain/model/AppearanceProfile.kt`
- `app/src/main/java/io/legado/app/ui/workspace/WorkspaceScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/home/HomeScreen.kt`
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookRouteScreen.kt`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-wallpaper-tab.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-preview-tab.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-theme-ink-miuix-night.png`

## Rui ro con lai

- Moi wallpaper asset co noi dung sang can canh warning/overlay de khong lam mat doc duoc.
