# P03.T05 - Import/export `.drductheme`

## Ket qua

Trang thai: `DONE`

## Trien khai

- Dinh nghia format `.drductheme` v2 voi manifest versioned va checksum/mime map.
- Package co the chua `AppearanceProfile`, icon assets, wallpaper assets va cover albums.
- Import co preview truoc khi commit, collision thi tao ban sao va khong cho path traversal.
- Security gate chan zip bomb, checksum mismatch, MIME mismatch, asset qua size va SVG doc hai.
- Khong cho executable hoac unreferenced content di qua pipeline import.

## Verification

- `ThemePackageSecurityPolicyTest`: PASS.
- `:app:compileAppDebugKotlin`: PASS.
- `:app:assembleAppDebug`: PASS.

## Bang chung

- `app/src/main/java/io/legado/app/help/config/ThemePackageManager.kt`
- `app/src/main/java/io/legado/app/help/config/ThemePackageSecurityPolicy.kt`
- `app/src/test/java/io/legado/app/help/config/ThemePackageSecurityPolicyTest.kt`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-preview-tab.png`

## Rui ro con lai

- Tang format version ve sau can giu backward-compatible validation va preview gate.
