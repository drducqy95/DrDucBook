# P03.T02 - Trung tam ca nhan hoa Compose/MVI

## Ket qua

Trang thai: `DONE`

## Trien khai

- Tao trung tam ca nhan hoa Compose/MVI gom 4 tab: `Chu de`, `Bieu tuong`, `Hinh nen`, `Xem truoc`.
- Dung `PersonalizationContract`, `PersonalizationViewModel`, `PersonalizationScreen` va `PersonalizationRouteScreen`.
- Ho tro `Apply`, `Discard`, `Reset`, clone, rename, delete va canh bao thay doi chua luu.
- Draft duoc giu qua `SavedStateHandle`; active profile chi doi khi nguoi dung Apply.
- Route screen gom activity result picker, back handling va toast/effect handling.

## Verification

- `:app:compileAppDebugKotlin`: PASS.
- `:app:assembleAppDebug`: PASS.
- Visual QA co `p3-center-theme.png`, `p3-theme-tab.png`, `p3-preview-tab.png`.

## Bang chung

- `app/src/main/java/io/legado/app/ui/personalization/PersonalizationContract.kt`
- `app/src/main/java/io/legado/app/ui/personalization/PersonalizationRouteScreen.kt`
- `app/src/main/java/io/legado/app/ui/personalization/PersonalizationScreen.kt`
- `app/src/main/java/io/legado/app/ui/personalization/PersonalizationViewModel.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-center-theme.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-theme-tab.png`
- `docs/plans/drducbook-rebuild-2026/artifacts/phase03/p3-preview-tab.png`

## Rui ro con lai

- Profile editor ngan gon va trung tam nhap lieu can giu veto cho unsaved-change flow o P11.
